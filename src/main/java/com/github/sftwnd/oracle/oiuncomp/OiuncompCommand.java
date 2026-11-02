package com.github.sftwnd.oracle.oiuncomp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The command-line utility {@code oiuncomp [-c | -d <directory>] [-f] <file_>...}.
 * <p>
 * Compressed files of Oracle distributions are marked with {@code _} at the end of the name. The uncompressed file
 * gets the name without it and is created next to the compressed file or in the {@code -d} directory; the compressed
 * file is kept. An existing file is overwritten only with {@code -f}, and a partially written result is removed. With
 * {@code -c} the data goes to the standard output. An error in one file does not stop the processing of the others.
 */
public final class OiuncompCommand {

    /** Exit status: all files are uncompressed. */
    static final int SUCCESS = 0;
    /** Exit status: some files are not uncompressed. */
    static final int FAILURE = 1;
    /** Exit status: invalid arguments. */
    static final int USAGE_ERROR = 2;

    private static final String NAME = "oiuncomp";
    private static final String SUFFIX = "_";
    private static final String USAGE = String.join(System.lineSeparator(),
            "usage: " + NAME + " [-c | -d <directory>] [-f] <file_>...",
            "Uncompresses files compressed by Oracle Installer (their names end with '" + SUFFIX + "').",
            "  -c              write the uncompressed data to the standard output",
            "  -d <directory>  write the files to <directory> instead of the directory of each file",
            "  -f              overwrite existing files",
            "  -h, --help      print this help",
            "");

    private final PrintStream out;
    private final PrintStream err;

    OiuncompCommand(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    /**
     * Runs the utility; on an error the process exits with a non-zero status.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        int status = new OiuncompCommand(System.out, System.err).run(args);
        if (status != SUCCESS) {
            System.exit(status);
        }
    }

    /** Runs the command and returns the exit status. */
    int run(String... args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println(NAME + ": " + e.getMessage());
            err.print(USAGE);
            return USAGE_ERROR;
        }
        if (options.help) {
            out.print(USAGE);
            return SUCCESS;
        }
        int status = SUCCESS;
        for (String file : options.files) {
            if (!uncompress(file, options)) {
                status = FAILURE;
            }
        }
        out.flush();
        if (out.checkError()) {
            err.println(NAME + ": error writing the standard output");
            status = FAILURE;
        }
        return status;
    }

    private boolean uncompress(String file, Options options) {
        try {
            Path source = Paths.get(file);
            if (options.toStandardOutput) {
                try (InputStream input = Files.newInputStream(source)) {
                    Oiuncomp.uncompress(input, out);
                }
                return true;
            }
            Path target = target(source, options.directory);
            if (target == null) {
                report(file, "the name does not end with '" + SUFFIX + "'");
                return false;
            }
            write(source, target, options.overwrite);
            return true;
        } catch (IOException | InvalidPathException e) {
            report(file, describe(e));
            return false;
        }
    }

    /** The name of the uncompressed file is that of the compressed one without {@code _}; {@code null} if absent. */
    private static Path target(Path source, Path directory) {
        Path name = source.getFileName();
        String fileName = name == null ? "" : name.toString();
        if (fileName.length() <= SUFFIX.length() || !fileName.endsWith(SUFFIX)) {
            return null;
        }
        String targetName = fileName.substring(0, fileName.length() - SUFFIX.length());
        return directory == null ? source.resolveSibling(targetName) : directory.resolve(targetName);
    }

    private static void write(Path source, Path target, boolean overwrite) throws IOException {
        OpenOption[] openOptions = overwrite
                ? new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING}
                : new OpenOption[] {StandardOpenOption.CREATE_NEW};
        try (InputStream input = Files.newInputStream(source)) {
            // Opened apart from writing: only a file opened by this command may be removed
            OutputStream output = Files.newOutputStream(target, openOptions);
            try {
                try (OutputStream data = output) {
                    Oiuncomp.uncompress(input, data);
                }
            } catch (IOException | RuntimeException e) {
                deleteQuietly(target, e);
                throw e;
            }
        }
    }

    private static void deleteQuietly(Path file, Exception cause) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            cause.addSuppressed(e);
        }
    }

    private void report(String file, String message) {
        err.println(NAME + ": " + file + ": " + message);
    }

    private static String describe(Exception e) {
        if (e instanceof NoSuchFileException) {
            return "no such file or directory: " + ((NoSuchFileException) e).getFile();
        }
        if (e instanceof FileAlreadyExistsException) {
            return ((FileAlreadyExistsException) e).getFile() + " already exists, use -f to overwrite it";
        }
        if (e instanceof AccessDeniedException) {
            return "permission denied: " + ((AccessDeniedException) e).getFile();
        }
        if (e instanceof OiuncompException) {
            return "corrupted data: " + e.getMessage();
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** The parsed command-line arguments. */
    private static final class Options {

        private final List<String> files = new ArrayList<>();
        private boolean toStandardOutput;
        private boolean overwrite;
        private boolean help;
        private Path directory;

        /** @throws IllegalArgumentException invalid arguments; the message explains why */
        static Options parse(String... args) {
            Options options = new Options();
            boolean optionsEnded = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (optionsEnded || !arg.startsWith("-") || arg.equals("-")) {
                    options.files.add(arg);
                } else if (arg.equals("--")) {
                    optionsEnded = true;
                } else if (arg.equals("-c")) {
                    options.toStandardOutput = true;
                } else if (arg.equals("-f")) {
                    options.overwrite = true;
                } else if (arg.equals("-h") || arg.equals("--help")) {
                    options.help = true;
                } else if (arg.equals("-d")) {
                    if (++i == args.length) {
                        throw new IllegalArgumentException("option -d requires a directory");
                    }
                    options.directory = directory(args[i]);
                } else {
                    throw new IllegalArgumentException("unknown option " + arg);
                }
            }
            if (options.help) {
                return options;
            }
            if (options.files.isEmpty()) {
                throw new IllegalArgumentException("no files to uncompress");
            }
            if (options.toStandardOutput && options.directory != null) {
                throw new IllegalArgumentException("options -c and -d cannot be used together");
            }
            return options;
        }

        private static Path directory(String name) {
            try {
                return Paths.get(name);
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException("invalid directory " + name, e);
            }
        }
    }
}
