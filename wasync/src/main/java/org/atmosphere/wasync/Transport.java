/*
 * Copyright 2008-2025 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.wasync;

/**
 * Define a supported {@link Transport}. Default are websocket, streaming, server-side events and long-polling.
 * Use this interface only if you want to define another transport. New transport can be registered using the {@link Options}
 * class.
 *
 * @author Jeanfrancois Arcand
 */
public interface Transport {

    /**
     * The transport name
     *
     * @return transport name
     */
    Request.TRANSPORT name();

    /**
     * Register a new {@link FunctionResolver}
     *
     * @param function {@link FunctionResolver}
     * @return this;
     */
    Transport registerF(FunctionWrapper function);

    /**
     * Called when an unexpected exception ocurred.
     *
     * @param t a {@link Throwable}
     */
    void onThrowable(Throwable t);

    /**
     * Close the underlying transport}
     */
    void close();

    /**
     * Return the current {@link org.atmosphere.wasync.Socket.STATUS}
     */
    Socket.STATUS status();

    /**
     * Error handled by a {@link Function}
     */
    boolean errorHandled();

    /**
     * Set a {@link Throwable}
     *
     * @param e {@link Throwable}
     */
    void error(Throwable e);

    /**
     * Set the {@link Future}, which can be used to clone the connection.
     *
     * @param f {@link Future}
     */
    void future(Future f);

    /**
     * Set the {@link Future}, which will unlock the {@link Socket#fire} method once the connection has been fully established.
     * @param f {@link Future}
     */
    void connectedFuture(Future f);
}
