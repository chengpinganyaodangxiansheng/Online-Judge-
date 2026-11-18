package org.example.judge.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.Container;
import lombok.extern.slf4j.Slf4j;
import org.example.judge.domain.SandboxPoolStats;
import org.example.judge.domain.SandboxResource;
import org.example.judge.mapper.SandboxResourceMapper;
import org.example.judge.service.SandboxResourceService;
import org.example.judge.service.SandboxEnvironmentService;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** 一次性就绪池：数据库保存总额度，队列只缓存本代次已验证的可领取资源。 */
@Slf4j
public class DockerSandBoxPool {
    /** 容器引擎客户端，用于测试环境的创建、查询、故障注入和回收。 */
    private final DockerClient dockerClient;

    /** 执行环境记录访问对象，用于状态迁移、归属核对和分项回收证据保存。 */
    private final SandboxResourceMapper mapper;

    /** 环境额度预留服务，用于在创建资源前原子检查正常容量和总额度。 */
    private final SandboxResourceService resources;

    /** 执行环境生命周期服务，创建并验证环境，或按分项证据回收环境。 */
    private final SandboxEnvironmentService environments;

    /** 稳定工作节点标识，限定本池可恢复和回收的资源归属。 */
    private final String worker;

    /** 本次进程启动生成的代次标识，用于拒绝旧代次资源的迟到状态变更。 */
    private final String generation = UUID.randomUUID().toString();

    /** 正常容量目标，包含预留、创建、就绪和已占用环境，不等于始终保留的空闲数量。 */
    private final int normal;

    /** 完整执行环境的总额度上限；待回收及创建结果未知的环境仍占用额度。 */
    private final int hard;

    /** 容量固定的就绪容器编号队列，只保存本代次已验证且尚未使用的容器；出队后仍须竞争数据库占用权。 */
    private final BlockingQueue<String> ready;

    /** 以容器编号关联本进程已登记的资源记录，供领取、退役和路径查询使用。 */
    private final Map<String, SandboxResource> known = new ConcurrentHashMap<>();

    /** 本地已撤销使用资格的容器编号集合，数据库故障时仍保留退役意图。 */
    private final Set<String> retired = ConcurrentHashMap.newKeySet();

    /** 协调领取、归还、退役和发布就绪资源的本地互斥锁，防止交错操作重新发布失效容器。 */
    private final Object lifecycle = new Object();

    /** 启动资源核对是否已完成；未完成前禁止补池和周期回收入口继续处理。 */
    private volatile boolean initialized;

    /**
     * 建立一次性容器池，接收容器客户端、持久化访问对象、额度服务和环境服务。
     * 工作节点标识应跨重启保持稳定；启动代次则由当前对象独立生成。
     * 正常容量至少为一，总额度不得小于正常容量且不得超过一百二十八。
     * 构造阶段只建立有界就绪队列，不创建容器；实际恢复和预热由初始化方法完成。
     */
    public DockerSandBoxPool(
            DockerClient dockerClient,
            SandboxResourceMapper mapper,
            SandboxResourceService resources,
            SandboxEnvironmentService environments,
            String worker,
            int normal,
            int hard) {
        // 检查正常容量是否小于一、总额度是否小于正常容量、总额度是否超过一百二十八；任一条件成立都说明配置不可用。
        if (normal < 1 || hard < normal || hard > 128) {
            // 拒绝创建配置错误的容器池，把错误交给启动流程报告。
            throw new IllegalArgumentException("沙箱容量配置无效");
        }
        // 保存容器引擎客户端，后面通过它查询和停止实际容器。
        this.dockerClient = dockerClient;
        // 保存数据库访问对象，后面通过它查询记录并更新资源状态。
        this.mapper = mapper;
        // 保存额度预留服务，创建环境之前必须由它申请额度。
        this.resources = resources;
        // 保存环境服务，具体的创建、自检和回收交给它处理。
        this.environments = environments;
        // 保存本节点身份，查询和恢复时只处理属于本节点的资源。
        this.worker = worker;
        // 保存希望维持的正常资源数量，供补池时检查容量缺口。
        this.normal = normal;
        // 保存所有未关闭环境的总额度上限，包括仍在回收中的环境。
        this.hard = hard;
        // 创建容量固定的就绪队列；例如正常容量为四，最多缓存四个可领取的容器编号。
        this.ready = new ArrayBlockingQueue<>(normal);
    }

    /**
     * 启动时核对数据库资源记录与引擎中的实际容器，旧资源只进入回收，不重新执行提交。
     * 先撤销本节点未关闭记录，再接管可确认归属的旧版容器或缺失记录的容器。
     * 任一步查询或归属核对失败都会向调用方抛出异常，不能按零占用继续预热。
     * 核对结束后开放定时维护入口，并尝试补充一份环境；后续周期补池逐步达到正常容量。
     */
    public void initDockerPool() {
        // 查询本节点所有未关闭环境并持久化退役；即使容器已经消失，附属资源仍需核对。
        // 查询本节点尚未关闭的全部资源，逐条处理；每次循环中的资源对象代表一条数据库记录。
        // 重启后，检查之前的节点，把之前的节点全部打上回收的标记，在数据库中进行。
        for (SandboxResource resource : mapper.active(worker)) {
            // 按资源编号在数据库标记退役，交给回收流程处理，不能仅删除内存记录就认为额度已释放。
            mapper.retire(resource.getResourceId());
        }
        // 同时列出运行与停止的容器，避免漏掉停止后仍占资源额度的残留。
        // 查询包含已停止容器在内的完整列表并逐个核对，防止重启后遗漏实际仍存在的容器。
        for (Container container : dockerClient.listContainersCmd().withShowAll(true).exec()) {
            // 仅接管名称与本工作节点旧版固定槽位规则匹配的容器，不接管其他应用资源。
            // 确认引擎返回了容器名称数组后才遍历；没有名称时跳过按旧版名称识别的步骤。
            if (container.getNames() != null) {
                // 逐个检查当前容器的名称，判断它是否符合本节点以前使用的命名规则。
                for (String name : container.getNames()) {
                    // 检查名称是否由斜杠、本节点标识、短横线和数字组成；节点标识先按普通文字转义，避免其中的符号被当成正则规则。
                    if (name.matches("/" + Pattern.quote(worker) + "-[0-9]+")) {
                        // 创建旧版容器的补登记对象，随后填入回收所需的身份信息。
                        SandboxResource legacy = new SandboxResource();
                        // 为补登记记录生成独立资源编号，避免与已有记录混淆。
                        legacy.setResourceId(UUID.randomUUID().toString());
                        // 将旧容器归属到当前工作节点。
                        legacy.setWorkerId(worker);
                        // 标记这是旧版本遗留资源，不能当成本次启动新建的就绪资源。
                        legacy.setGeneration("legacy");
                        // 去掉引擎名称开头的斜杠，保存创建和查询时使用的容器名称。
                        legacy.setContainerName(name.substring(1));
                        // 补记旧版容器的确切身份，使其纳入持久化回收和总额度统计。
                        // 保存引擎返回的确切容器编号，回收时可准确定位容器。
                        legacy.setContainerId(container.getId());
                        // 把旧版容器补记到数据库，纳入回收和总额度统计。
                        mapper.adoptLegacy(legacy);
                    }
                }
            }
            // 先检查容器存在标签，再由下一行核对工作节点标签是否等于本节点；两项都满足才处理。
            if (container.getLabels() != null && worker.equals(container.getLabels().get("oj.worker"))) {
                // 用资源标签定位数据库记录；标签和名称必须相互印证，不能只凭名称猜测归属。
                // 读取容器的资源编号标签，用这个编号查询对应的数据库记录。
                SandboxResource record = mapper.find(container.getLabels().get("oj.resource"));
                // 数据库没有记录，或记录声称已经关闭但容器还存在时，需要核实身份并补建回收记录。
                if (record == null || "CLOSED".equals(record.getState())) {
                    // 取出容器标签中声明的资源编号，接下来校验格式和名称是否一致。
                    String identity = container.getLabels().get("oj.resource");
                    // 按系统约定组合预期容器名，用它与引擎返回的实际名称交叉核对。
                    String expectedName = "oj-" + worker + "-" + identity;
                    // 以下任一情况都拒绝接管：没有资源编号、编号格式不合法、没有名称数组、实际名称中找不到预期名称。
                    if (identity == null || !identity.matches("[0-9a-f-]{36}")
                            || container.getNames() == null || !Arrays.asList(container.getNames())
                            .contains("/" + expectedName)) {
                        // 停止初始化并报告归属不明确，避免误回收其他应用的容器。
                        throw new IllegalStateException("无法确认沙箱归属");
                    }
                    // 创建恢复记录，用来接管已经确认归属但数据库记录缺失的容器。
                    record = new SandboxResource();
                    // 沿用容器标签中的资源编号，使容器与数据库记录重新对应。
                    record.setResourceId(identity);
                    // 记录本节点归属，后续回收查询才能找到这条记录。
                    record.setWorkerId(worker);
                    // 标记为启动恢复资源，区别于本次启动创建的资源。
                    record.setGeneration("recovered");
                    // 保存已经通过实际名称核验的容器名。
                    record.setContainerName(expectedName);
                    // 保存引擎中的真实容器编号，避免仅凭名称猜测容器身份。
                    record.setContainerId(container.getId());
                    // 对缺失或已关闭但仍实际存在的容器补建回收记录，重新纳入计账。
                    // 将实际存在的容器重新纳入数据库回收记录和额度统计。
                    mapper.adoptOrphan(record);
                }
                // 保存查询确认的确切容器编号，消除创建响应丢失造成的身份不确定。
                // 将已核实的容器编号写回资源记录，补齐创建响应丢失时缺失的信息。
                mapper.created(record.getResourceId(), container.getId());
            }
        }
        // 所有启动核对都成功后才开放维护入口，允许定时补池和回收。
        initialized = true;
        // 尝试预留额度并创建一个就绪环境，后续定时补池逐步达到配置容量。
        replenish();
    }

    /**
     * 兼容的阻塞领取入口，每次等待一秒并反复调用有界领取方法，直到取得容器。
     * 返回已获得资源占用权的容器编号；此方法本身没有总等待上限。
     * 线程中断时恢复中断标志并抛出异常，可靠消息消费者使用另一有界入口控制总等待时间。
     */
    public String getContainer() {
        // 每次等待一秒，没拿到就继续等待；这是兼容入口，本身没有总等待期限。
        // 消息消费者使用有期限的领取入口，避免长期阻塞后无法及时处理排队超时。
        try {
            // 声明本次领取结果，空值表示这一轮没有拿到可用容器。
            String result;
            // 至少尝试领取一次，再根据领取结果决定是否继续等待。
            do {
                // 本轮最多等待一秒，并通过数据库竞争资源占用权。
                result = pollContainer(1000);
            // 若本轮领取结果为空，就再次等待；因此这个兼容入口没有整体等待时限。
            } while (result == null);
            // 把成功领取的容器编号交给调用方。
            return result;
        // 等待期间收到线程中断时进入这里，结束等待并向调用方报告。
        } catch (InterruptedException interrupted) {
            // 恢复线程的中断标志，让更外层的取消或关闭流程仍能识别中断。
            Thread.currentThread().interrupt();
            // 保留原始中断原因，转换为运行时异常交给调用方。
            throw new IllegalStateException("等待沙箱时被中断", interrupted);
        }
    }

    /**
     * 在指定毫秒数内等待一个就绪编号，再通过数据库条件更新竞争本代次的资源占用权。
     * 返回容器编号表示资源领取成功，不代表已经取得业务任务的执行权。
     * 超时、编号失效、已退役或条件竞争失败时返回空值；等待中断与数据库异常向上传播。
     * 数据库结果未知时先加入本地退役集合，防止再次发布该资源，后续由回收入口核对。
     */
    public String pollContainer(long milliseconds) throws InterruptedException {
        // 有界阻塞出队只负责等待资源；数据库占用权检查在拿到编号后执行。
        // 在指定毫秒数内从就绪队列取出一个编号；超时仍没有编号就得到空值。
        String id = ready.poll(milliseconds, TimeUnit.MILLISECONDS);
        // 检查是否缺少容器编号；缺少编号就直接结束本次处理，不能继续查询或操作容器。
        if (id == null) {
            // 本轮未能取得有效容器，返回空值，由调用方决定等待、重试或结束。
            return null;
        }
        // 取得本地生命周期锁，让领取、归还、退役和发布队列的关键步骤互斥执行。
        synchronized (lifecycle) {
            // 根据容器编号查找本进程登记的资源记录，取得数据库资源身份。
            SandboxResource resource = known.get(id);
            // 编号没有本地记录，或已经被标记为退役时，不能把它交给任务使用。
            if (resource == null || retired.contains(id)) {
                // 本轮未能取得有效容器，返回空值，由调用方决定等待、重试或结束。
                return null;
            }
            try {
                // 同时检查就绪状态和启动代次，只有影响一条记录才确认独占领取成功。
                // 数据库同时核对就绪状态和启动代次；恰好更新一条记录才表示本线程成功取得独占使用权。
                if (mapper.acquire(resource.getResourceId(), generation) == 1) {
                    // 返回已经成功取得占用权的容器编号。
                    return id;
                }
            } catch (RuntimeException failure) {
                // 在本地记录禁止再次使用的容器编号，即使数据库暂时不可用也能阻止重新领取。
                retired.add(id);
                // 把原始异常继续抛给调用方，由上层处理本次失败。
                throw failure;
            }
            // 本轮未能取得有效容器，返回空值，由调用方决定等待、重试或结束。
            return null;
        }
    }

    /**
     * 将尚未写入提交文件、尚未执行用户代码的容器归还就绪队列。
     * 参数为空、编号未知、资源已退役或已经在队列中时直接忽略，防止重复发布。
     * 调用方负责确保资源确实未使用；数据库仅按本代次与占用状态竞争归还。
     * 归还不关闭资源记录，也不释放环境总额度；队列无法接纳时改为退役回收。
     */
    public void returnContainer(String id) {
        // 检查是否缺少容器编号；缺少编号就直接结束本次处理，不能继续查询或操作容器。
        if (id == null) {
            // 结束当前方法，本次调用不再执行后续资源操作。
            return;
        }
        // 取得本地生命周期锁，让领取、归还、退役和发布队列的关键步骤互斥执行。
        synchronized (lifecycle) {
            // 根据容器编号查找本进程登记的资源记录，取得数据库资源身份。
            SandboxResource resource = known.get(id);
            // 未知资源、已退役资源或已经在就绪队列中的资源都不再归还，避免发布失效资源或重复编号。
            if (resource == null || retired.contains(id) || ready.contains(id)) {
                // 结束当前方法，本次调用不再执行后续资源操作。
                return;
            }
            // 先持久化恢复就绪状态，再非阻塞入队；短路条件保证竞争失败时不会错误发布。
            // 先尝试把本代次已占用但未使用的记录恢复就绪；只有更新成功才执行下一行入队操作。
            if (mapper.releaseUnused(resource.getResourceId(), generation) == 1
                    // 入队失败说明队列无法接纳该资源；只有数据库归还成功且入队失败时才进入退役分支。
                    && !ready.offer(id)) {
                // 把无法继续安全使用的容器交给退役流程，随后由后台回收。
                retire(id);
            }
        }
    }

    /**
     * 返回本地就绪队列是否非空，供暂停消费后的恢复探测使用，不出队也不占用资源。
     * 这是瞬时提示而非领取承诺，真正领取时仍须重新检查资源状态与数据库条件。
     */
    public boolean hasAvailableContainer() {
        // 返回就绪队列此刻是否有编号；这只是可用性提示，实际领取仍要竞争数据库占用权。
        return !ready.isEmpty();
    }

    /**
     * 读取本节点所有未关闭记录，汇总创建、就绪、占用和回收数量以及总额度。
     * 统计以完整环境记录为单位，不能用实际容器个数代替仍有附属残留的环境数量。
     * 返回本次查询的监控快照；数据库查询失败或出现未知状态时抛出异常。
     */
    public SandboxPoolStats statistics() {
        // 统计来源是数据库中的未关闭环境，不是空闲队列大小。
        // 一个容器即使已经删除，只要目录或控制组尚未清理完，仍要计入总占用。
        // 创建本轮统计结果，分别累计各状态数量。
        SandboxPoolStats stats = new SandboxPoolStats();
        // 读取本节点未关闭记录，包含创建中、就绪、占用和回收中的资源。
        List<SandboxResource> active = mapper.active(worker);
        // 把未关闭记录总数作为当前已占用的环境额度。
        stats.setTotal(active.size());
        // 同时返回配置的总额度，便于观察离上限还有多少空间。
        stats.setHardCapacity(hard);
        // 逐条查看未关闭资源，把每条记录计入对应状态的数量。
        for (SandboxResource resource : active) {
            // 按数据库状态分流，一条资源只能计入它当前所在的状态。
            switch (resource.getState()) {
                // 已预留额度但尚未就绪，计入创建中的资源。
                case "RESERVED":
                    // 创建中数量加一。
                    stats.setCreating(stats.getCreating() + 1);
                    // 结束当前状态分支，避免接着执行其他状态的计数。
                    break;
                // 已完成验证并等待领取，计入就绪资源。
                case "READY":
                    // 就绪数量加一。
                    stats.setReady(stats.getReady() + 1);
                    // 结束当前状态分支，避免接着执行其他状态的计数。
                    break;
                // 已被任务领取，计入正在占用的资源。
                case "LEASED":
                    // 占用数量加一。
                    stats.setLeased(stats.getLeased() + 1);
                    // 结束当前状态分支，避免接着执行其他状态的计数。
                    break;
                // 已经退役但尚未全部清理完成，仍占额度，计入回收中的资源。
                case "RECYCLING":
                    // 回收中数量加一。
                    stats.setRecycling(stats.getRecycling() + 1);
                    // 结束当前状态分支，避免接着执行其他状态的计数。
                    break;
                // 遇到未定义的状态时进入这里，不能按正常数据继续统计。
                default:
                    // 报告数据库状态不符合约定，避免返回不完整的统计。
                    throw new IllegalStateException("未知的资源计账状态");
            }
        }
        // 返回本次查询得到的容量和状态数量快照。
        return stats;
    }

    /**
     * 按确切容器编号请求引擎强制终止，阻止本轮继续执行用户程序。
     * 本方法只发起终止，不删除容器、不清理目录，也不归还资源总额度。
     * 容器不存在或状态冲突仅记录信息，其他调用异常向上传播；最终回收证据由回收服务确认。
     */
    public void terminate(String id) {
        try {
            // 向容器引擎实际发送强制终止命令，尽快停止该容器中的程序。
            dockerClient.killContainerCmd(id).exec();
        } catch (NotFoundException missing) {
            // 容器可能已被并发回收；不存在不代表目录、控制组等资源已完成清理。
            log.debug("终止沙箱时容器已不存在，保留资源记录等待回收核对，容器编号={}", id);
        } catch (ConflictException conflict) {
            // 引擎状态冲突可能来自容器已停止，不能仅凭冲突响应确认回收完成。
            log.warn("终止沙箱发生状态冲突，等待回收器核对，容器编号={}", id, conflict);
        }
    }

    /**
     * 单向撤销指定容器的使用资格：先标记本地退役并从就绪队列移除，再尝试持久化。
     * 未知编号和空值直接忽略，已使用容器不能通过归还入口重新发布。
     * 数据库写入失败时保留本地标记，后续回收扫描会重试落库；此处不确认资源已经释放。
     */
    public void retire(String id) {
        // 检查是否缺少容器编号；缺少编号就直接结束本次处理，不能继续查询或操作容器。
        if (id == null) {
            // 结束当前方法，本次调用不再执行后续资源操作。
            return;
        }
        // 取得本地生命周期锁，让领取、归还、退役和发布队列的关键步骤互斥执行。
        synchronized (lifecycle) {
            // 根据容器编号查找本进程登记的资源记录，取得数据库资源身份。
            SandboxResource resource = known.get(id);
            // 本进程没有登记这个容器时，无法可靠取得资源身份，进入当前方法的拒绝或结束分支。
            if (resource == null) {
                // 结束当前方法，本次调用不再执行后续资源操作。
                return;
            }
            // 先阻断本地再领取，再处理可能失败的数据库写入，避免故障时容器被重新使用。
            // 在本地记录禁止再次使用的容器编号，即使数据库暂时不可用也能阻止重新领取。
            retired.add(id);
            // 从就绪队列删除此编号，阻止后续领取到已退役容器。
            ready.remove(id);
            try {
                // 按资源编号在数据库标记退役，交给回收流程处理，不能仅删除内存记录就认为额度已释放。
                mapper.retire(resource.getResourceId());
            } catch (RuntimeException unavailable) {
                log.warn("沙箱退役待处理，容器编号={}", id);
            }
        }
    }

    /**
     * 串行执行一轮回收：先补写本地退役意图，再处理数据库中的待回收环境。
     * 每轮最多处理总额度数量的候选；单个环境回收失败只记录告警，留待下轮重试。
     * 环境服务分别核对容器、目录、控制组及连接，只有持久记录已关闭才移除本地追踪。
     * 方法锁避免本对象的回收扫描重叠；补池使用另一入口，可在额度允许时独立推进。
     */
    public synchronized void recoverRetired() {
        // 先把内存里没来得及落库的退役意图补写，再扫描数据库中的待回收环境。
        // 整个方法串行执行，防止两轮扫描同时回收同一资源；补池使用独立调度入口。
        // 检查启动时的资源核对是否尚未完成；未完成就立即返回，避免在旧资源尚未查清时回收或补池。
        if (!initialized) {
            // 结束当前方法，本次调用不再执行后续资源操作。
            return;
        }
        // 遍历本地所有已禁止使用的容器编号，尝试补写之前可能未成功落库的退役状态。
        for (String id : retired) {
            // 根据容器编号查找本进程登记的资源记录，取得数据库资源身份。
            SandboxResource resource = known.get(id);
            // 只有本地还能查到资源记录时，才能拿到数据库编号并补写退役状态。
            if (resource != null) {
                // 按资源编号在数据库标记退役，交给回收流程处理，不能仅删除内存记录就认为额度已释放。
                mapper.retire(resource.getResourceId());
            }
        }
        // 记录本轮遇到的待回收候选数量，限制单轮处理规模。
        int processed = 0;
        // 查询本节点尚未关闭的全部资源，逐条处理；每次循环中的资源对象代表一条数据库记录。
        for (SandboxResource resource : mapper.active(worker)) {
            // 不是回收中状态就直接跳过；否则先用计数旧值与总额度比较，再把计数加一。旧值已达到上限也跳过，限制本轮最多处理总额度那么多条。
            if (!"RECYCLING".equals(resource.getState()) || processed++ >= hard) {
                // 跳过当前资源，进入循环中的下一条记录。
                continue;
            }
            try {
                // 分项回收并保存证据；调用返回后仍查询关闭状态，不能把部分回收当成全部完成。
                // 委托环境服务分别回收容器、工作目录、资源控制组和通信连接，并保存回收证据。
                environments.recycle(resource);
                // 重新查询数据库确认资源已全部关闭，再由下一行确认记录中存在容器编号，两项满足才删除本地追踪。
                if ("CLOSED".equals(mapper.find(resource.getResourceId()).getState()) && resource.getContainerId() != null) {
                    // 全部回收已确认后，删除容器编号与资源记录的本地映射。
                    known.remove(resource.getContainerId());
                    // 从本地退役集合删除已回收编号，之后无需再次补写退役状态。
                    retired.remove(resource.getContainerId());
                }
            } catch (RuntimeException failure) {
                log.warn("沙箱回收待确认，资源编号={}", resource.getResourceId(), failure);
            }
        }
    }

    /**
     * 尝试补充一份全新执行环境，不根据空闲数量下降盲目扩容。
     * 额度服务在短事务内同时检查正常容量缺口与总额度，返回空值表示本轮不应创建。
     * 环境服务完成容器创建和可信执行器就绪验证后，才登记身份并放入就绪队列。
     * 创建异常由持久化资源记录继续追踪；本入口只记录延后，不把未知结果当作已释放额度。
     */
    public void replenish() {
        // 检查启动时的资源核对是否尚未完成；未完成就立即返回，避免在旧资源尚未查清时回收或补池。
        if (!initialized) {
            // 结束当前方法，本次调用不再执行后续资源操作。
            return;
        }
        // 先持久化唯一资源身份并原子预留额度，成功后才允许创建任何环境专属资源。
        // 在数据库事务中核对容量并预留一份额度；正常容量已够或总额度已满时会返回空值。
        SandboxResource resource = resources.reserve(worker, generation, normal, hard);
        // 预留服务返回空值，表示正常容量已够或总额度已满，本轮不创建任何新环境。
        if (resource == null) {
            // 结束当前方法，本次调用不再执行后续资源操作。
            return;
        }
        try {
            // 创建、连接并验证执行器；返回的记录应已就绪，创建中的环境不交给消费者。
            // 根据预留记录创建完整执行环境，并等待执行器通过就绪验证。
            SandboxResource created = environments.create(resource);
            // 取得本地生命周期锁，让领取、归还、退役和发布队列的关键步骤互斥执行。
            synchronized (lifecycle) {
                // 先登记编号对应的持久身份，再发布到有界队列；队列满时保守退役。
                // 先把新容器编号与持久资源记录关联起来，使领取线程能查到身份。
                known.put(created.getContainerId(), created);
                // 尝试立即将新编号放入就绪队列；返回失败表示队列已满，需要退役这个环境。
                if (!ready.offer(created.getContainerId())) {
                    // 撤销本次新建容器的使用资格，交给后台回收，避免出现未被队列管理的闲置资源。
                    retire(created.getContainerId());
                }
            }
        } catch (RuntimeException failure) {
            log.warn("沙箱补充已延后，资源编号={}", resource.getResourceId(), failure);
        }
    }

    /**
     * 按本进程已登记的容器编号，计算工作目录下的专属代码目录路径。
     * 未知编号抛出参数异常；此方法仅返回路径，不创建目录、不验证目录是否已经存在。
     * 路径使用内部容器名称生成，不直接拼接外部传入的目录内容。
     */
    public String getCodeDir(String id) {
        // 先确认编号属于本池登记的环境，再用内部容器名称组合目录，避免接受任意外部路径。
        // 根据容器编号查找本进程登记的资源记录，取得数据库资源身份。
        SandboxResource resource = known.get(id);
        // 本进程没有登记这个容器时，无法可靠取得资源身份，进入当前方法的拒绝或结束分支。
        if (resource == null) {
            // 拒绝为未知容器生成工作路径，防止使用未经登记的身份。
            throw new IllegalArgumentException("未知的沙箱容器");
        }
        // 把项目运行目录、代码池目录和已登记容器名称组合成路径，再转换成字符串返回。
        return Path.of(System.getProperty("user.dir"),
                        "user-code-pool", resource.getContainerName()).toString();
    }
}
