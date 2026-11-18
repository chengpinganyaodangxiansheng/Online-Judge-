# 本文件描述如何制作判题执行器镜像。镜像保存程序和运行环境，容器是它启动后的运行实例。
# 构建例子：在项目根目录执行 docker build -t oj-sandbox:local ./sandbox-runtime
# -t 为镜像命名，最后的目录是构建上下文，下面 COPY 的源路径均相对这个目录。
# 构建镜像时执行 COPY、RUN；启动容器时才执行末尾的 ENTRYPOINT。
# 运行需要后端配置资源组挂载、工作目录等条件，镜像构建成功只说明程序已打包。

# FROM 指定基础镜像，这里提供第八版开发工具包，包含 java 和 javac。
# 冒号后是镜像标签，@sha256 后是固定内容摘要，用于锁定所用基础镜像。
FROM openjdk:8-jdk-alpine@sha256:94792824df2df33402f201713f932b58cb9de94a0cd524164a0f2283343547b3

# COPY 将本机构建上下文中的源码目录复制到镜像内指定目录。
# 例子：本机 sandbox-runtime/src/SandboxManager.java 会放到 /opt/sandbox/src/SandboxManager.java。
COPY src /opt/sandbox/src

# RUN 在构建镜像期间执行命令。
# mkdir 创建存放字节码的目录，&& 表示只有目录创建成功才继续编译。
# javac 编译源码，-encoding 指定编码，-d 指定编译结果保存位置。
# *.java 匹配该目录下所有以 .java 结尾的文件。
# 例子：SandboxManager.java 编译后生成 /opt/sandbox/classes/SandboxManager.class。
RUN mkdir /opt/sandbox/classes && javac -encoding UTF-8 -d /opt/sandbox/classes /opt/sandbox/src/*.java

# 将启动脚本和阶段入口脚本一起复制到镜像内。
# 例子：start.sh 会变成镜像中的 /opt/sandbox/start.sh。
COPY start.sh enter.sh /opt/sandbox/

# 为脚本设置权限。755 表示所有者可读写执行，所属组和其他用户可读和执行。
# *.sh 匹配脚本文件；这里使两个入口脚本都具备执行权限。
RUN chmod 755 /opt/sandbox/*.sh

# ENTRYPOINT 指定容器默认启动命令，数组中的每一项都是独立参数。
# /bin/sh 是命令解释器，第二项是让它读取执行的脚本路径。
# 完整流程例子：后端启动此镜像的容器，执行 start.sh，随后启动 SandboxManager。
# 管理程序接到源码后通过 enter.sh 编译，再通过 enter.sh 逐个运行测试用例。
ENTRYPOINT ["/bin/sh", "/opt/sandbox/start.sh"]
