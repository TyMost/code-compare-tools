import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public class TempDiff {
    public static void main(String[] args) throws Exception {
        File repoDir = new File("d:/Coding/code-compare-tools/examples/projectA-git/.git");
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir)
                .readEnvironment()
                .findGitDir()
                .build()) {
            ObjectId base = repository.resolve("refs/heads/main^{tree}");
            ObjectId targetCommit = repository.resolve("refs/heads/feature/git-demo");
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                oldTree.reset(reader, base);
            }
            DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            formatter.setRepository(repository);
            FileTreeIterator newTree = new FileTreeIterator(repository);
            List<DiffEntry> entries = formatter.scan(oldTree, newTree);
            System.out.println("entries=" + entries.size());
            for (DiffEntry entry : entries) {
                System.out.println("entry=" + entry.getChangeType() + " newPath=" + entry.getNewPath());
                FileHeader fh = formatter.toFileHeader(entry);
                byte[] buffer = fh.getBuffer();
                System.out.println("bufferLen=" + (buffer == null ? -1 : buffer.length));
                System.out.println("hunkCount=" + fh.getHunks().size());
                for (HunkHeader h : fh.getHunks()) {
                    System.out.println("hunk start=" + h.getStartOffset() + " end=" + h.getEndOffset());
                    int start = h.getStartOffset();
                    int end = h.getEndOffset();
                    if (buffer != null && end > start) {
                        String slice = new String(buffer, start, end - start, StandardCharsets.UTF_8);
                        System.out.println("slice=<<<" + slice + ">>>");
                    }
                }
            }
            formatter.close();
        }
    }
}
