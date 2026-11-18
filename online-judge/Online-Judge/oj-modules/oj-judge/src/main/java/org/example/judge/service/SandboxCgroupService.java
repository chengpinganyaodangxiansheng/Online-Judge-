package org.example.judge.service;

/** Docker Desktop 下仅管理项目专属的 cgroup 委派，不修改全局资源配置。 */
public interface SandboxCgroupService {
    /** 按资源唯一编号和字节制内存预算建立专属受控子树；先验证预留资格并登记操作，成功后返回可供容器挂载的管理路径。 */
    String create(String resourceId, long memoryBytes);

    /** 按资源编号调用受控入口回收专属子树；操作失败向上传播，调用方不能据此标记控制组已回收或提前归还环境额度。 */
    void remove(String resourceId);
}
