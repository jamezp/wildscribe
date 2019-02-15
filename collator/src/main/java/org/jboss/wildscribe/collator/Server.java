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

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.core.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) this could likely use some general clean-up
class Server implements AutoCloseable {

    private final StandaloneCommandBuilder builder;

    private Process process;
    private boolean running;
    private ModelControllerClient client;
    private Thread shutdownHook;

    Server(final Path jbossHome) {
        this.builder = StandaloneCommandBuilder.of(jbossHome)
                .setServerConfiguration("standalone-full-ha.xml");
    }

    synchronized void start() throws IOException, TimeoutException, InterruptedException {
        if (!running) {
            process = Launcher.of(builder)
                    .inherit()
                    .launch();
            shutdownHook = ProcessHelper.addShutdownHook(process);
            // TODO (jrp) these need to be configurable
            client = ModelControllerClient.Factory.create("localhost", 9990);
            ServerHelper.waitForStandalone(client, 60);
            running = true;
        }
    }

    private synchronized void stop() throws IOException {
        if (running) {
            try {
                ServerHelper.shutdownStandalone(client, 60);
                client.close();
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                running = false;
            } finally {
                process.destroyForcibly();
            }
        }
    }

    synchronized ModelNode executeOp(final ModelNode op) throws IOException {
        check();
        return client.execute(op);
    }

    synchronized ModelNode executeOp(final Operation op) throws IOException {
        check();
        return client.execute(op);
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    private synchronized void check() {
        if (!running) {
            throw new IllegalStateException("The server has not been started.");
        }
    }
}
