# 亮点7验收 case 矩阵

范围：Windows Docker Desktop、单后端、多容器；测试库固定为 oj_reliability_test。所有 case 都需要本轮报告，不用历史文档代替。M=确定性规则/模拟故障测试，I=真实数据库/Docker，E=真实HTTP/MQ链路。

|编号|需求|输入/故障与明确断言|自动化入口|层级|
|---|---|---|---|---|
|7-01|预热一次性池|同worker两次attempt及重启，容器ID各不相同，已退役不可回池|SandboxEnvironmentIntegrationTest.poolRecyclesAcrossAttemptsAndRestart|I|
|7-02|提交内编译一次|三个用例只有一个index=-1编译证据、每例静态变量均为1|SandboxRuntimeIntegrationTest.everyWritableAreaAndJvmStateAreResetBetweenCases|I|
|7-03|进程与文件清理|首例遗留sleep子进程和文件，后例干净且cleanupComplete=true|SandboxRuntimeIntegrationTest.sequentialCasesCleanResidualFilesAndProcesses|I|
|7-04|全部用户可写区|work/case、tmp、dev/shm每例写文件，下一例均无旧文件|everyWritableAreaAndJvmStateAreResetBetweenCases|I|
|7-05|并发隔离|两容器同时各6例排序与工作区写入，结果不串扰|concurrentSubmissionsKeepIndependentWorkspaces|I|
|7-06|编译产物限额|1025内部类，编译失败且不得执行用例|compilationArtifactLimitReturnsCompileEvidence|I|
|7-07|权限和软链接|用户不可写cgroup/产物、不可读管理文件，清理软链接不跟随|userCannotAccessManagementOrMutateArtifacts、userJvmHasNoEffectiveCapabilities|I|
|7-08|容器安全配置|无网络、只读根、非特权、无Docker socket、外层swap=memory|createdEnvironmentIsReclaimedIdempotently|I|
|7-09|内核限额生效|运行中读取memory.max、swap.max、cpu.max、pids.max|runningCaseHasKernelMemorySwapCpuAndPidLimits|I|
|7-10|CPU超限|死循环及子进程死循环，CPU计账后TLE|cpuLimitStopsInfiniteLoop、childProcessCpuIsChargedToTheCase|I|
|7-11|墙钟保护|sleep不耗CPU仍受墙钟终止|wallClockGuardStopsSleepingTask|I|
|7-12|内存证据|堆内与堆外分配触发本例OOM，禁止后续用例|kernelOomIsRecordedForCurrentCase、offHeapAllocationTriggersKernelMemoryEvidence|I|
|7-13|不能伪造MLE|stderr写OOM并退出137，无OOM证据则RE|forgedOomTextAndExit137RemainRuntimeError|I|
|7-14|多原因分类|遍历MLE/OLE/TLE组合，运行优先级固定，编译均CE|SandboxVerdictTest.classificationIsIndependentOfReasonArrivalOrder|M|
|7-15|输出流限制|stdout、stderr、交替、无换行；65536允许/65537超限；输出保留有界|outputLimitHasBoundedEvidence、outputStreamsAreIndependentlyBounded、outputByteBoundaryIsExact|I|
|7-16|接收端有界|分片帧、超大长度、慢接收、断连，队列有界且已有证据保留|SandboxProtocolCallbackTest|M|
|7-17|失败短路|先成功再退出7，保留前例，第三例不执行|runtimeFailureStopsLaterCasesAndPreservesEarlierEvidence|I|
|7-18|跨例优先级|先WA后MLE返回WA，不套用同例优先级|SandboxVerdictTest.earlierWrongAnswerWinsOverLaterMemoryLimit|M|
|7-19|清理失败规则|清理未确认不得AC，已确认失败不被回收异常覆盖|SandboxVerdictTest.cleanupFailurePreservesFailureButPreventsSuccess|M|
|7-20|总预算和监督故障|不读消息、破坏监督器、SIGKILL监督器、断控制连接，观察真实容器停止|totalBudgetStopsRuntimeWithoutResultConsumption、supervisorFailureStopsContainer、killedSupervisorCannotLeaveUnprotectedTaskRunning、controlDisconnectStopsCurrentSubmission|I|
|7-21|租约与旧回调|失租禁止提交、固定预算不能续长、看门狗与迟到续租竞争|JudgeConsumerTest、JudgeReliabilityIntegrationTest|M/I|
|7-22|完整环境配额|并发16次预留不越N，未知创建及部分回收不释放H|SandboxResourceIntegrationTest|I|
|7-23|回收失败恢复|持续子树回收失败，容器已删仍占H；重启不补建，恢复只归还一次|residualCgroupsExhaustQuotaUntilConfirmedRecovery|I|
|7-24|创建中断恢复|未发起外部请求可回收；已建子树/Docker前中断可恢复；未知请求保留|unstartedCreationCanCloseButUnknownCreationCannot、restartReclaimsConfirmedCgroupBeforeDockerRequest|I|
|7-25|等待背压|空池不领取RUNNING不ACK，DB断开暂停，资源恢复后继续，prefetch=1|JudgeConsumerTest、JudgeTaskConfigTest、JudgeConsumerPauseIntegrationTest|M/I|
|7-26|排队截止|到期不能领取/重试，权威状态记录失败|JudgeReliabilityIntegrationTest.queueDeadlineBlocksClaimBeforeScannerAndNeverRetries|I|
|7-27|慢回收独立|回收阻塞不阻塞补池调度|JudgeTaskConfigTest.slowRecyclingDoesNotBlockReplenishment|M|
|7-28|进程/磁盘容量|进程数、inode、字节耗尽后管理器仍能清理并执行后例|processLimitDoesNotStarveManagement、fileCountIsBoundedAndNextCaseIsClean、writableByteCapacityIsBoundedAndRecovered|I|
|7-29|正式判题链路|HTTP七种判定，attempt=1，阶段证据入库，幂等与权限|JudgeSubmissionHttpIntegrationTest|E|
|7-30|后端硬退出|领取后强杀Judge，替代进程租约恢复，旧attempt失效且结果唯一|workerCrashAfterClaimRecoversOnNewProcess|E|
|7-31|性能对照|相同代码/JDK编译一次，三轮交错运行6例，含创建/清理耗时|SandboxReuseBenchmarkTest|I|

说明：M层证据不能冒充真实内核故障注入。上述case覆盖核心交付边界，不宣称穷尽所有线程调度或达到生产规模容量证明。具体执行状态见本轮结果报告。
