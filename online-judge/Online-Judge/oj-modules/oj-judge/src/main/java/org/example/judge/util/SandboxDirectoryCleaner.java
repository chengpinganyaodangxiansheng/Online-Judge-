package org.example.judge.util;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;

/** 深度优先清理专属目录，不跟随软链接，不将全部文件路径收集进内存。 */
public class SandboxDirectoryCleaner extends SimpleFileVisitor<Path> {
    /** 删除遍历到的文件或软链接目录项并返回继续遍历；不读取链接目标，删除异常向上传播以保留未完成清理的证据。 */
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        // 遍历器逐个把文件交到这里；删除符号链接时删除的是链接本身，不进入链接指向的目录。
        Files.delete(file);
        // 通知遍历器继续处理其他条目；例如目录中有两个文件，删完第一个后继续处理第二个。
        return FileVisitResult.CONTINUE;
    }

    /** 子项处理完后删除当前目录，包括清理根目录；若遍历传入异常则先抛出，不把部分删除误报为清理完成。 */
    @Override
    public FileVisitResult postVisitDirectory(Path directory, IOException failure)
            throws IOException {
        if (failure != null) {
            // 非空表示遍历当前目录时已经遇到异常，先抛出原始异常，不掩盖尚未清完的状态。
            throw failure;
        }
        Files.delete(directory);
        // 子文件已在前面的回调中删除，现在删除空目录；删除失败会继续向调用方报告。
        return FileVisitResult.CONTINUE;
    }
}
