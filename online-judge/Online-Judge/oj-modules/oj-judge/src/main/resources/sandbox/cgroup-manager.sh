#!/bin/sh
# 仅管理本项目 UUID 对应的独占子树；禁止接受任意路径或命令。
# -e	一般情况下，命令执行失败就终止脚本
# -u	使用未定义的变量时，终止脚本
set -eu
action=$1
identity=$2
case "$identity" in *[!0-9a-f-]*|'') exit 64;; esac
[ "${#identity}" -eq 36 ] || exit 64
root="/sys/fs/cgroup/oj-runtime-$identity"
case "$action" in
  create)
    memory=$3
    case "$memory" in *[!0-9]*|'') exit 64;; esac
    [ "$memory" -ge 67108864 ] && [ "$memory" -le 4294967296 ] || exit 64
    mkdir "$root"
    chmod 700 "$root" #chmod 700：设置目录权限。700 代表：所有者：可读、可写、可执行（对目录来说，可执行意味着可以进入或遍历）。所属组：无权限。其他用户：无权限。
    echo "$memory" > "$root/memory.max"
    echo 0 > "$root/memory.swap.max"
    echo 128 > "$root/pids.max"
    echo '100000 100000' > "$root/cpu.max"
    echo '+cpu +memory +pids' > "$root/cgroup.subtree_control"
    [ "$(cat "$root/memory.swap.max")" = 0 ] # $(...)命令替换，拿到命令的输出结果。
    ;;
  remove)
    # 容器确认不存在后才调用；非空组 rmdir 失败，绝不靠递归文件删除掩盖残留。
    # -d 检查路径是否存在并且是目录。
    # -q 表示安静模式，只判断是否匹配，不输出内容。
    # find：主要查找文件和目录  grep：主要查找文件里面的文本内容。
    # find "$root"：遍历这个资源的控制组目录
    # -depth 采用先处理子目录、后处理父目录的顺序。
    # -type d 只查找目录。
    # -exec rmdir '{}' \; 对找到的每个目录执行 rmdir。
    if [ -d "$root" ]; then # populated 0：当前 cgroup 及其子 cgroup 都没有存活的成员进程。populated 1：当前 cgroup 或其子组仍存在成员进程。
      grep -q '^populated 0$' "$root/cgroup.events" # cgroup.events 是 Linux 内核提供的状态文件。其中可能包含：(populated 0 frozen 0 )或者：(populated 1  frozen 0)
      find "$root" -depth -type d -exec rmdir '{}' \;
      [ ! -d "$root" ] # 验证根目录已经删除
    fi
    ;;
  *) exit 64;;
esac
