import java.io.IOException;

/** 已确认的编译产物数量或体积超限，与平台文件系统故障分开分类。 */
public final class ArtifactLimitException extends IOException {
    /** 构造已确认产物限额异常，保存固定诊断而不携带用户路径或源码；管理程序据此形成编译失败证据，普通文件系统异常不使用此类型。 */
    public ArtifactLimitException() {
        super("编译产物数量或大小超出限制");
    }
}
