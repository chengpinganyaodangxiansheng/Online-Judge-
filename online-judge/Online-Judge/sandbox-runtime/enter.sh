#!/bin/sh
# 这个脚本由管理程序调用，负责将编译器或用户程序放进指定资源组，再降低运行权限。
# 调用例子一：/bin/sh /opt/sandbox/enter.sh /cg/task-001/compile compile
# 调用例子二：/bin/sh /opt/sandbox/enter.sh /cg/task-001/case-0 run 3 5
# 第二个例子会启动用户程序，并让启动方法收到两个字符串参数“3”和“5”。

# 普通命令失败或使用未设置变量时停止，避免在错误的资源组或权限下运行代码。
set -eu

# $1 是第一个参数，即资源组路径；$2 是第二个参数，即编译或运行模式。
# 命令解释器的赋值语法要求等号两边没有空格。
group=$1
mode=$2

# 丢弃前两个位置参数，后面的用例参数向前移动。
# 例子：原参数为“目录、run、3、5”，执行后 $1 为 3，$2 为 5。
shift 2

# $$ 是当前进程编号，> 将编号写入内核控制文件，把当前进程移入指定资源组。
# 后续启动的子进程会继承组归属，因此编译器、用户程序及其子进程都受到限制。
echo $$ > "$group/cgroup.procs"

# < 表示从文件读取输入；read 将一行拆分后保存到后面的五个变量。
# IFS='|' 指定竖线为分隔符；-r 表示保留反斜杠原样，不将其当作转义符。
# 五个字段依次是资源组路径、实际时间截止点、处理器时间预算、阶段状态、终止原因。
# 例子：/cg/task-001/case-0|100000|2000000|ACTIVE|0
# 读完后 active_group 是资源组路径，state 是 ACTIVE，flags 是 0。
# 状态文件可能没有末尾换行，read 即使读到数据也可能返回失败。
# || 后的 -n 检查第一个字段非空，让这种“有数据但没有换行”的记录可以继续校验。
IFS='|' read -r active_group wall cpu state flags < /control/stage || [ -n "$active_group" ]

# 检查记录属于本次资源组、阶段仍在执行、没有终止原因。
# = 比较字符串，&& 连接必须同时满足的条件，|| exit 75 表示校验失败就退出。
# 先进入组再检查，可以处理“监督程序正好同时终止任务”的情况。
# 例子：状态已变成 DONE，或 flags 已变成 1，脚本就不会继续启动用户程序。
[ "$active_group" = "$group" ] && [ "$state" = ACTIVE ] && [ "$flags" = 0 ] || exit 75

# cd 切换当前工作目录；用户代码使用相对路径创建文件时，会以这个目录为起点。
# 例子：程序创建 result.txt，实际落在 /work/case/result.txt。
cd /work/case

# ulimit 设置当前进程及后续子进程继承的资源上限；-f 限制单个文件可增长的大小。
# 32768 是命令解释器定义的块数，不是字节数，也不是整个目录的总容量。
# 按每块五百一十二字节计算时约为十六兆字节，具体单位以镜像内解释器实现为准。
# 例子：程序持续向一个文件写数据，达到该上限后继续写入会受系统限制。
ulimit -f 32768

# case 按模式选择分支，右括号结束分支条件，两个分号结束该分支，esac 结束整个选择结构。
# su 切换用户；nobody 是低权限账户，防止用户代码以管理员身份修改控制文件。
# -s 指定命令解释器，-c 后跟要执行的固定命令文本。
# 单引号中的内容由被启动的解释器处理；源码和测试参数均不拼接进命令文本。
case "$mode" in
  # 编译模式：javac 将源码编译为字节码。
  # -encoding UTF-8 指定源码编码，-d 指定输出目录，最后一个参数是源码路径。
  # 例子：编译 Solution.java 后通常生成 /work/case/Solution.class。
  compile) exec su -s /bin/sh -c 'exec javac -encoding UTF-8 -d /work/case /work/case/Solution.java' nobody ;;

  # 运行模式：从受保护的 /artifact 目录加载 Solution 类并调用启动方法。
  # 初始堆为八兆字节，最大堆为五百一十二兆字节；整个进程仍受资源组内存限额约束。
  # 外层 "$@" 将 shift 后的所有参数逐个传递，双引号保留每个参数原有的边界。
  # -- 结束 BusyBox su 的选项解析，避免把以负号开头的用例参数当作选项。
  # sandbox-user 用作内层解释器的第零个参数，后面的参数才进入内层 "$@"。
  # 内层 "$@" 再将这些参数传给用户程序，避免参数内容被当作命令重新解释。
  # 例子：传入 3 和 5 时，用户启动方法的参数数组长度为 2，两项分别是“3”和“5”。
  # java	启动 JVM
  #-cp	指定 Classpath（类路径）
  #/artifact	存放 .class 文件的目录
  #Solution	要执行的 Java 主类
  run) exec su -s /bin/sh -c 'exec java -XX:-UsePerfData -Xms8m -Xmx512m -cp /artifact Solution "$@"' -- nobody sandbox-user "$@" ;;

  # 星号匹配其他所有模式；例如传入 delete，就以错误码 70 退出。
  *) exit 70 ;;
esac
