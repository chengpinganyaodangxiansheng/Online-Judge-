package org.example.judge.callback;

import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.common.core.enums.CodeRunStatus;

@Data
@Slf4j//DockerStartResultCallback 是一个自定义的回调类，用于处理 Docker 容器执行命令时的实时输出流。它继承自 Docker Java 客户端库中的 ExecStartResultCallback，通常用于异步获取容器内命令的执行结果（标准输出和标准错误）
public class DockerStartResultCallback extends ExecStartResultCallback {

    private CodeRunStatus codeRunStatus;  //记录执行成功还是失败

    private String errorMessage;

    private String message;

    /**
     * 执行每次指令都能够返回给调用方该指令是否成功  每当有新的输出帧（stdout 或 stderr）时被调用。  这个方法在每个输出帧到达时被调用。Frame 对象包含流类型和实际数据（字节数组）。
     * @param frame
     */
    @Override
    public void onNext(Frame frame) {
        StreamType streamType = frame.getStreamType();//获取流类型
        if (StreamType.STDERR.equals(streamType)) {
            if (StrUtil.isEmpty(errorMessage)) {
                errorMessage = new String(frame.getPayload());
            } else {
                errorMessage = errorMessage + new String(frame.getPayload());
            }
            codeRunStatus = CodeRunStatus.FAILED;
        } else {
            String msgTmp = new String(frame.getPayload());
            if (StrUtil.isNotEmpty(msgTmp)) {
                message = new String(frame.getPayload());
            }
            codeRunStatus = CodeRunStatus.SUCCEED;
        }
        super.onNext(frame);//这行调用父类的 onNext，确保父类的逻辑也被执行（例如，父类可能内部维护了输出流的累积）。通常建议保留。
    }
}