package org.gradle.wrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path project = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path propertiesPath = project.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) {
            properties.load(input);
        }

        String distributionUrl = properties.getProperty("distributionUrl");
        if (distributionUrl == null || distributionUrl.isBlank()) {
            throw new IOException("distributionUrl is missing from gradle-wrapper.properties");
        }

        URI distribution = URI.create(distributionUrl);
        String fileName = Path.of(distribution.getPath()).getFileName().toString();
        String folderName = fileName.replaceFirst("-bin\\.zip$", "");
        Path cache = Path.of(
                System.getProperty("user.home"),
                ".gradle",
                "wrapper",
                "dists",
                "locate-cardboard",
                folderName
        );
        Path archive = cache.resolve(fileName);
        Path installation = cache.resolve(folderName);

        if (!Files.isRegularFile(installation.resolve(executableName()))) {
            Files.createDirectories(cache);
            if (!Files.isRegularFile(archive)) download(distribution, archive);
            verifyAgainstPublishedChecksum(distribution, archive);
            unzip(archive, cache);
        }

        Path executable = installation.resolve(executableName());
        executable.toFile().setExecutable(true);
        ProcessBuilder builder = new ProcessBuilder();
        builder.command().add(executable.toString());
        for (String argument : args) builder.command().add(argument);
        builder.directory(project.toFile());
        builder.inheritIO();
        System.exit(builder.start().waitFor());
    }

    private static String executableName() {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? "bin/gradle.bat"
                : "bin/gradle";
    }

    private static void download(URI source, Path destination) throws IOException {
        System.out.println("Downloading " + source);
        HttpURLConnection connection = (HttpURLConnection) source.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
    }

    private static void verifyAgainstPublishedChecksum(URI source, Path archive) throws Exception {
        URI checksumUri = URI.create(source.toString() + ".sha256");
        HttpURLConnection connection = (HttpURLConnection) checksumUri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        String expected;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            expected = reader.readLine().trim().toLowerCase();
        } finally {
            connection.disconnect();
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(archive)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(expected)) {
            Files.deleteIfExists(archive);
            throw new IOException("The downloaded Gradle archive failed checksum verification.");
        }
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = normalizedDestination.resolve(entry.getName()).normalize();
                if (!output.startsWith(normalizedDestination)) {
                    throw new IOException("Unsafe entry in Gradle distribution: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
