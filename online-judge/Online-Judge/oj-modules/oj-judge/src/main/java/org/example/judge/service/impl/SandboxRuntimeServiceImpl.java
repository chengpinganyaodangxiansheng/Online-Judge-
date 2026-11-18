package org.example.judge.service.impl;

import com.github.dockerjava.api.DockerClient;
import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.judge.domain.*;
import org.example.judge.service.SandboxRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** 持续 attach 连接的受控传输实现，两侧缓冲有界且断线不恢复旧执行。 */
@Service
public class SandboxRuntimeServiceImpl implements SandboxRuntimeService {
    /** 容器引擎客户端，在这里负责接通可信执行器的输入输出流。 */
    @Autowired
    private DockerClient dockerClient;

    /** 以容器编号索引专属通信连接；关闭失败时保留记录供继续回收。 */
    private final ConcurrentHashMap<String, SandboxRuntimeConnection> connections = new ConcurrentHashMap<>();

    /** 连接必须唯一；失败清理局部管道，不把半初始化连接发布为就绪。 */
    @Override
    public void connect(String id) {
        try {
            // 先建立本地管道，再登记容器与连接的对应关系，后续发送和接收都通过容器编号找到它。
            SandboxRuntimeConnection connection = new SandboxRuntimeConnection();
            // 原子登记保证同一容器只保留一条连接。竞争失败只关闭新连接，不能关闭别人已登记的连接。
            if (connections.putIfAbsent(id, connection) != null) {
                // 关闭此连接持有的管道和回调，释放本地通信资源。
                connection.close();
                // 已有连接时拒绝覆盖，防止正在执行的任务失去通信通道。
                throw new IllegalStateException("执行器连接重复");
            }
            try {
                // 接通容器的实时输入输出；关闭历史日志回放，避免把旧输出当成本次协议消息。
                // 收到的字节交给回调拼成完整消息，当前线程不在这里同步等待判题完成。
                dockerClient.attachContainerCmd(id)
                        // 接收管理程序的标准输出，可信协议结果从这里返回。
                        .withStdOut(true)
                        // 同时接收容器错误流，供回调识别异常通信。
                        .withStdErr(true)
                        // 把本地输入管道接到容器标准输入，用来发送请求。
                        .withStdIn(connection.input)
                        // 持续跟随新产生的输出，保持连接以接收后续阶段结果。
                        .withFollowStream(true)
                        // 禁用历史日志回放，避免旧字节混入本次协议解析。
                        .withLogs(false)
                        // 启动通信，并将后续数据交给指定回调处理。
                        .exec(connection.callback);
            } catch (RuntimeException failure) {
                // 接通失败后回收已登记的管道，再把故障抛给创建环境的调用方，不能发布半成品环境。
                // 接通失败时清理已经登记的连接，避免遗留半初始化管道。
                close(id);
                // 保留原始故障向上抛出，交给环境创建流程处理。
                throw failure;
            }
        } catch (IOException failure) {
            throw new IllegalStateException("执行器通信管道不可用", failure);
        }
    }

    /** 十秒内必须收到 READY，普通容器 running 状态不能替代执行器自检。 */
    @Override
    public void awaitReady(String id) {
        // 最多等待十秒接收执行器的第一条消息，用它确认自检是否完成。
        SandboxRuntimeMessage message = receive(id, 10000);
        // 没有消息表示等待超时，类型不是一表示没有收到约定的就绪消息，两种情况都拒绝发布环境。
        if (message == null || message.getType() != 1) {
            throw new IllegalStateException("执行器尚未就绪");
        }
    }

    /** 固定协议字段和大小，期望答案不进入用户容器。 */
    @Override
    public void submit(String id, String attempt, JudgeSubmitDTO snapshot, long budgetMillis) {
        try {
            // 至少要有一个用例且最多一百二十八个，防止无意义提交或用例数量失控。
            if (snapshot.getInputList().isEmpty() || snapshot.getInputList().size() > 128) {
                throw new IllegalArgumentException("测试用例数量超出限制");
            }
            // 按双方约定的顺序编码请求：类型、执行身份、源码、预算、用例数量及输入。
            // 接收端必须按相同顺序读取，不能随意调整字段先后。
            // 创建内存缓冲，先组装完整请求，再计算总长度并发送。
            /** buffer 负责存字节 `data` 负责把整数、字符串等内容转换成字节，再写进 buffer */
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            // 包装缓冲，使用固定二进制格式写整数、长整数和字节内容。
            DataOutputStream data = new DataOutputStream(buffer);
            // 写入请求类型十，接收端据此确认这是一份执行提交。
            data.writeInt(10);
            // 写入本次通信身份，最多一百二十八字节。
            writeText(data, attempt, 128);
            // 写入待编译源码，最多二十六万二千一百四十四字节。
            writeText(data, snapshot.getUserCode(), 262144);
            // 写入单个用例的处理器时间预算，单位为毫秒。
            data.writeLong(snapshot.getTimeLimit());
            // 题库使用千字节，执行器使用字节；精确乘法在溢出时抛异常，避免传入错误限额。
            data.writeLong(Math.multiplyExact(snapshot.getSpaceLimit(), 1024));
            // 写入整次提交剩余的总时间预算，防止每个阶段重新获得完整时限。
            data.writeLong(budgetMillis);
            // 写入用例数量，接收方按这个数量读取后续输入字段。
            data.writeInt(snapshot.getInputList().size());
            // 按题目快照中的顺序逐个读取用例输入，发送顺序就是执行顺序。
            for (String input : snapshot.getInputList()) {
                // 空输入按空字符串发送，每个用例输入最多一万六千三百八十四字节。
                writeText(data, input == null ? "" : input, 16384);
            }
            // 除了每个字段限长，还限制整份请求总大小，防止大量合法小字段组合成超大请求。
            if (buffer.size() > 4 * 1024 * 1024) {
                throw new IllegalArgumentException("请求帧大小超出限制");
            }
            // 找到本容器连接的发送管道，包装成可写二进制数据的流。
            DataOutputStream sender = new DataOutputStream(connection(id).sender);
            // 先写总长度，再写内容并刷新，让对端知道这一份请求何时收齐。
            // 先发送整帧字节数，接收方才能知道本条请求的边界。
            sender.writeInt(buffer.size());
            // 把已经组装完成的请求内容写入发送管道。
            buffer.writeTo(sender);
            // 将缓冲内容立即推向管道，避免对端一直等待尚未刷出的字节。
            sender.flush();
        } catch (IOException failure) {
            throw new IllegalStateException("向执行器发送数据失败", failure);
        }
    }

    /** 解析完整帧，必需字段缺失或帧尾有额外数据时按通信错误处理。 */
    @Override
    public SandboxRuntimeMessage receive(String id, long milliseconds) {
        try {
            // 回调已经处理了网络分片，这里拿到的是完整帧；超时未收到则返回空值供上层轮询。
            byte[] bytes = connection(id).callback.receive(milliseconds);
            // 规定时间内没有完整消息时直接返回空值，让上层继续检查取消和超时。
            if (bytes == null) {
                // 表示本轮未收到消息，并不表示用户程序运行失败。
                return null;
            }
            // 把完整帧包装成按固定二进制格式读取字段的数据流。
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
            // 创建本次解析结果，按协议顺序逐项填充。
            SandboxRuntimeMessage message = new SandboxRuntimeMessage();
            // 读取消息类型，一表示就绪，二表示阶段证据，三表示结束。
            message.setType(data.readInt());
            // 读取本次执行的通信身份，交给上层核对响应是否属于当前提交。
            message.setAttempt(readText(data, 128));
            // 就绪和结束消息只有公共字段，类型二才携带具体阶段的退出、超限和清理证据。
            if (message.getType() == 2) {
                // 读取阶段编号，负一为编译，非负数为运行用例。
                message.setIndex(data.readInt());
                // 读取合并的超限原因位，同一阶段可能同时存在多种超限。
                int flags = data.readInt();
                // 三个低位分别代表超时、输出超限、内存超限，可同时成立。
                // 检验是否合规，除了低3位其他位上是否存在数据，存咋则非法
                if ((flags & ~7) != 0) {
                    throw new IOException("执行凭据标志无效");
                }
                // 创建阶段结果对象，存储退出情况、资源超限和输出内容。
                SandboxStageResult stage = new SandboxStageResult();
                // 按位与一检查最低位是否置位，置位说明已确认超时。
                stage.setTimedOut((flags & 1) != 0);
                // 按位与二检查第二位，置位说明输出超限。
                stage.setOutputExceeded((flags & 2) != 0);
                // 按位与四检查第三位，置位说明内存超限。
                stage.setMemoryExceeded((flags & 4) != 0);
                // 读取主进程退出码，零通常表示正常退出。
                stage.setExitCode(data.readInt());
                // 读取该阶段消耗的处理器时间，单位为微秒。
                message.setCpuMicros(data.readLong());
                // 读取内存峰值原始数值，负值表示执行器没有取得有效测量。
                long peak = data.readLong();
                // 负数是执行器约定的“无法取得峰值”，转换为空值，避免显示成真实的负内存。
                message.setPeakBytes(peak < 0 ? null : peak);
                // 但布尔值传输时，也需要编码成字节。`writeBoolean()` 写入的格式是：
                //Java中的值	    实际写入的字节
                //false  	    00000000，即 0
                //true	        00000001，即 1
                int cleanup = data.readUnsignedByte();
                // 清理标志只能为零或一，其他数值都属于协议数据错误。
                if (cleanup > 1) {
                    throw new IOException("清理凭据无效");
                }
                // 一转换为已确认清理完成，零转换为未确认。
                stage.setCleanupComplete(cleanup == 1);
                // 读取阶段准备耗时，单位为微秒。
                message.setPreparationMicros(data.readLong());
                // 读取运行期间实际经过的时间，单位为微秒。
                message.setWallMicros(data.readLong());
                // 读取阶段清理耗时，单位为微秒。
                message.setCleanupMicros(data.readLong());
                if (message.getCpuMicros() < 0 || message.getPreparationMicros() < 0
                        || message.getWallMicros() < 0 || message.getCleanupMicros() < 0) {
                    throw new IOException("阶段计时凭据无效");
                }
                // 输出和错误各自限长，读取前检查长度，避免损坏消息让后端分配巨大数组。
                // 读取最多六万五千五百三十六字节的标准输出。
                stage.setOutput(readText(data, 65536));
                // 按相同限额读取错误输出，避免错误文本无限占用内存。
                stage.setError(readText(data, 65536));
                // 将解析完成的阶段证据放入消息对象。
                message.setEvidence(stage);
            // 不是阶段证据时，只允许就绪或结束类型；其他类型一律拒绝。
            } else if (message.getType() != 1 && message.getType() != 3) {
                throw new IOException("执行器消息无效");
            }
            // 预期字段读完后不应剩余字节。剩余数据可能意味着两端协议版本不一致。
            if (data.available() != 0) {
                throw new IOException("执行器消息包含多余数据");
            }
            // 全部字段校验通过后返回消息，交给上层处理执行状态。
            return message;
        } catch (InterruptedException interrupted) {
            // 恢复中断标志，便于外层关闭或取消流程识别中断。
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待执行器时被中断", interrupted);
        } catch (IOException failure) {
            throw new IllegalStateException("执行器数据帧格式错误", failure);
        }
    }

    /** 先关闭再移除；关闭失败保留连接记录，供资源回收继续处理。 */
    @Override
    public void close(String id) {
        // 按容器编号查找本进程已登记的通信连接。
        SandboxRuntimeConnection connection = connections.get(id);
        // 没有连接记录时进入当前方法的缺失处理分支，不能继续访问管道。
        if (connection == null) {
            // 连接已不存在，本次关闭操作直接结束。
            return;
        }
        try {
            // 必须关闭成功才移除追踪；按“编号和原连接对象”一起删除，避免误删并发替换的对象。
            // 关闭此连接持有的管道和回调，释放本地通信资源。
            connection.close();
            // 仅当映射仍是刚关闭的对象时删除，避免并发情况下误删其他连接。
            connections.remove(id, connection);
        } catch (IOException failure) {
            throw new IllegalStateException("执行器连接关闭尚未完成", failure);
        }
    }

    /** 未连接或已经回收的资源不能发送、接收或重连旧任务。 */
    private SandboxRuntimeConnection connection(String id) {
        // 按容器编号查找本进程已登记的通信连接。
        SandboxRuntimeConnection connection = connections.get(id);
        // 没有连接记录时进入当前方法的缺失处理分支，不能继续访问管道。
        if (connection == null) {
            throw new IllegalStateException("执行器连接不存在");
        }
        // 返回已经确认存在的连接供发送或接收操作使用。
        return connection;
    }

    /** 字符串按 UTF-8 字节限额传输，不把 Java 字符数误当字节数。 */
    private void writeText(DataOutputStream data, String value, int maximum) throws IOException {
        // 按统一编码转换为字节，协议限额按字节计算，中文字符可能占多个字节。
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        // 编码后的真实字节数超过该字段限额时拒绝发送。
        if (bytes.length > maximum) {
            throw new IllegalArgumentException("执行器字符串长度超出限制");
        }
        // 先写字符串字节数，使接收方能准确读取当前字段。
        data.writeInt(bytes.length);
        // 再写入字符串的实际编码内容。
        data.write(bytes);
    }

    /** 在分配前检查长度，损坏消息不能触发超大数组。 */
    private String readText(DataInputStream data, int maximum) throws IOException {
        // 不可信长度必须在分配数组之前检查，不能等读完或转成字符串后再判断是否超限。
        // 读取接收方声明的字段字节数，先校验再分配内存。
        int length = data.readInt();
        // 长度为负数或超过当前字段上限时拒绝解析，避免数组异常或过量内存分配。
        if (length < 0 || length > maximum) {
            throw new IOException("执行器字符串长度超出限制");
        }
        // 只按已验证的长度分配字节数组。
        byte[] bytes = new byte[length];
        // 必须读满声明长度，若数据提前结束会抛异常而不是返回半个字段。
        data.readFully(bytes);
        // 按发送方约定的编码恢复字符串并返回。
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
