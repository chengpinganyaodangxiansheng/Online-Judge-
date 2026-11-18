import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** 清空一个固定挂载点，保留挂载点本身。 */
public final class CleanupVisitor extends SimpleFileVisitor<Path> {
    /** 本次目录遍历的清理根目录，用于约束删除范围并保留所需根目录。 */
    private final Path root;

    /** 保存本次唯一允许的清理根。 */
    public CleanupVisitor(Path root) {
        this.root = root;
    }

    /** 文件和符号链接仅删除目录项本身。 */
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // 删除遍历到的文件后继续处理其他文件；默认遍历方式不会沿符号链接进入其他目录。
        Files.delete(file);
        return FileVisitResult.CONTINUE;
    }

    /** 子目录清空后删除，任何错误均中止下一用例。 */
    public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
        if (failure != null) {
            throw failure;
        }
        if (!root.equals(directory)) {
            // 根目录可能是容器挂载点，只清空内容；普通子目录则在内容删完后一起删除。
            Files.delete(directory);
        }
        return FileVisitResult.CONTINUE;
    }
}
