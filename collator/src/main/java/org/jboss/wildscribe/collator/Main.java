/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.wildscribe.collator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.plugin.core.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"ConstantConditions", "UseOfSystemOutOrSystemErr"})
public class Main {

    private static final PrintStream STDOUT = System.out;
    private static final PrintStream STDERR = System.err;
    private static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();
    private static final String[] DEFAULT_EXTENSIONS = {
            "org.jboss.as.xts",
            "org.wildfly.extension.datasources-agroal",
            "org.wildfly.extension.rts",
    };

    public static void main(final String[] args) throws Exception {
        final Properties userProperties = new Properties();
        if (args != null && args.length > 0) {
            final String arg = args[0];
            if ("-h".equalsIgnoreCase(arg) || "--help".equalsIgnoreCase(arg)) {
                printUsage(STDOUT);
                System.exit(0);
            }
            if ("-p".equalsIgnoreCase(arg) || "--properties".equalsIgnoreCase(arg)) {
                if (args.length == 1) {
                    fail("The argument %s expects a path value.", arg);
                }
                final Path path = validateDirectory(arg, args[1]);
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    userProperties.load(reader);
                }
            }
        }

        final Configuration config = Configuration.of(userProperties);

        final String home = config.get("jboss.home", "JBOSS_HOME");
        // TODO (jrp) we should use likely use a relative path of some sort to better work with from base directories
        final String modelDir = config.getOrDefault("model.directory", createPath("..", "models", "standalone"));
        final String generatedTargetDir = config.getOrDefault("generated.target.directory", createPath("..", "..", "wildscribe.github.io"));
        final String url = config.getOrDefault("url", "https://wildscribe.github.io");

        if (home == null) {
            fail("The jboss.home property or JBOSS_HOME environment variable is required.");
        }
        final Path jbossHome = validateDirectory("-home", home);
        if (!ServerHelper.isValidHomeDirectory(jbossHome)) {
            fail("Invalid server directory: %s%n", home);
        }
        final Path modelDirectory = validateDirectory(modelDir);
        final Path targetDirectory = validateDirectory(generatedTargetDir);

        // Start the server
        STDOUT.printf("Starting the server with JBOSS_HOME of: %s%n", jbossHome);
        try (Server server = new Server(jbossHome)) {
            server.start();

            // Add the additional extensions required
            final Collection<String> extensionsToAdd = new ArrayList<>();
            Collections.addAll(extensionsToAdd, DEFAULT_EXTENSIONS);
            Collections.addAll(extensionsToAdd, config.getArray("additional.extensions"));
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
            for (String ext : extensionsToAdd) {
                builder.addStep(Operations.createAddOperation(Operations.createAddress("extension", ext)));
            }
            server.executeOp(builder.build());

            String name = config.getOrDefault("server.name", resolveValue(server, "product-name"));
            if ("WildFly Full".equalsIgnoreCase(name)) {
                name = "WildFly";
            }
            final Version version = Version.parse(config.getOrDefault("server.version", resolveValue(server, "product-version")));

            final String binaryName = getBinaryBaseName(name, version);

            // Generate the DMR file
            final Path dmrFile = modelDirectory.resolve(binaryName + ".dmr");
            STDOUT.printf("Creating the DMR file: %s%n", dmrFile);
            org.jboss.wildscribe.modeldumper.Main.main(new String[] {dmrFile.toString()});
            addVersionTxtEntry(name, version, dmrFile);

            // Generate the messages file
            final Path msgFile = modelDirectory.resolve(binaryName + ".messages");
            STDOUT.printf("Creating message file: %s%n", msgFile);
            org.wildscribe.logs.Main.main(new String[] {jbossHome.toString(), msgFile.toString()});
        }

        // Generate the web site
        System.setProperty("url", url);
        STDOUT.printf("Generating site in %s%n", targetDirectory);
        org.jboss.wildscribe.site.Main.main(new String[] {modelDirectory.toString(), targetDirectory.toString()});
    }

    private static Path validateDirectory(final String value) {
        final Path result = Paths.get(value);
        if (Files.notExists(result)) {
            fail("The directory %s does not exist.%n", value);
        }
        if (!Files.isDirectory(result)) {
            fail("The directory %s is not valid.%n", value);
        }
        return result.toAbsolutePath();
    }

    private static Path validateDirectory(final String argName, final String value) {
        if (value == null) {
            fail("Argument %s is required.%n", argName);
        }
        final Path result = Paths.get(value);
        if (Files.notExists(result)) {
            fail("The directory %s does not exist.%n", value);
        }
        if (!Files.isDirectory(result)) {
            fail("The directory %s is not valid.%n", value);
        }
        return result.toAbsolutePath();
    }

    private static void printUsage(final PrintStream ps) {
        ps.println("Usage: java -jar wildscribe-collator.jar -p config.properties");
        printArg(ps, "-p,--properties", "The path to the properties file for the configuration.");
        printArg(ps, "-h,--help", "Prints the help");
    }

    private static void printArg(final PrintStream ps, final String arg, final String description) {
        ps.printf("    %-15s %s%n", arg, description);
    }

    private static void fail(final String msg) {
        STDERR.println(msg);
        printUsage(STDERR);
        System.exit(1);
    }

    private static void fail(final String fmt, final Object... args) {
        STDERR.printf(fmt, args);
        printUsage(STDERR);
        System.exit(1);
    }

    private static String getBinaryBaseName(final String name, final Version version) {
        return (name + "-" + version.toString()).replace(' ', '-');
    }

    private static Supplier<String> resolveValue(final Server server, final String attributeName) {
        return () -> {
            try {
                final ModelNode result = server.executeOp(Operations.createReadAttributeOperation(EMPTY_ADDRESS, attributeName));
                final ModelNode modelValue = Operations.readResult(result);
                if (!modelValue.isDefined()) {
                    fail("Could not determine value for attribute %s%n.", attributeName);
                }
                return modelValue.asString();
            } catch (IOException e) {
                fail("Failed to determine value for attribute %s: %s%n", attributeName, e.getMessage());
                // Should never happen
                throw new UncheckedIOException(e);
            }
        };
    }

    private static void addVersionTxtEntry(final String name, final Version version, final Path dmrFile) throws IOException {
        final Path dir = dmrFile.getParent();
        if (Files.notExists(dir)) {
            fail("Could not find parent directory %s%n", dir);
        }

        // WildFly:15.0:WildFly-15.0.0.Final.dmr
        final StringBuilder sb = new StringBuilder()
                .append(name)
                .append(':')
                .append(version.getMajor())
                .append('.')
                .append(version.getMinor());
        if (version.getMicro() > 0) {
            sb.append('.').append(version.getMicro());
        }
        sb.append(':')
                .append(dmrFile.getFileName().toString());

        final Path versionTxt = dir.resolve("versions.txt");
        if (Files.notExists(versionTxt)) {
            Files.createFile(versionTxt);
        }
        final String line = sb.toString();
        // First read the file and determine if we need to add a new line
        final List<String> lines = Files.readAllLines(versionTxt, StandardCharsets.UTF_8);
        for (String foundLine : lines) {
            if (line.equals(foundLine)) {
                return;
            }
        }
        // Add the line to the file
        try (BufferedWriter writer = Files.newBufferedWriter(versionTxt, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(line);
            writer.newLine();
            for (String toAppend : lines) {
                writer.write(toAppend);
                writer.newLine();
            }
        }
    }

    private static String createPath(final String first, String... more) {
        return Paths.get(first, more).toString();
    }
}
