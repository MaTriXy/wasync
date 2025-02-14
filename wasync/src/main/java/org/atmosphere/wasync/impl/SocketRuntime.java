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
package org.atmosphere.wasync.impl;

import static org.atmosphere.wasync.Event.MESSAGE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.atmosphere.wasync.Encoder;
import org.atmosphere.wasync.FunctionWrapper;
import org.atmosphere.wasync.Future;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.Socket;
import org.atmosphere.wasync.Transport;
import org.atmosphere.wasync.transport.TransportsUtil;
import org.atmosphere.wasync.transport.WebSocketTransport;
import org.atmosphere.wasync.util.FluentStringsMap;
import org.atmosphere.wasync.util.ReaderInputStream;
import org.atmosphere.wasync.util.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implement the logic for communicating with a remote server.
 *
 * @author Jeanfrancois Arcand
 */
public class SocketRuntime {

    private final static Logger logger = LoggerFactory.getLogger(SocketRuntime.class);

    protected Transport transport;
    protected final Options options;
    protected final DefaultFuture rootFuture;
    protected final List<FunctionWrapper> functions;

    public SocketRuntime(Transport transport, Options options, DefaultFuture rootFuture, List<FunctionWrapper> functions) {
        this.transport = transport;
        this.options = options;
        this.rootFuture = rootFuture;
        this.functions = functions;
    }

    public DefaultFuture future() {
        return rootFuture;
    }

    protected Object invokeEncoder(List<Encoder<? extends Object, ?>> encoders, Object instanceType) {
        for (Encoder e : encoders) {
            Class<?>[] typeArguments = TypeResolver.resolveArguments(e.getClass(), Encoder.class);

            if (typeArguments.length > 0 && typeArguments[0].isAssignableFrom(instanceType.getClass())) {
                instanceType = e.encode(instanceType);
            }
        }
        return instanceType;
    }

    public Future write(Request request, Object data) throws IOException {
        // Execute encoder
        Object object = invokeEncoder(request.encoders(), data);

        boolean webSocket = transport.name().equals(Request.TRANSPORT.WEBSOCKET);
        if (webSocket
                && (transport.status().equals(Socket.STATUS.CLOSE)
                || transport.status().equals(Socket.STATUS.ERROR))) {
            transport.error(new IOException("Invalid Socket Status " + transport.status().name()));
        } else {
            if (webSocket) {
                webSocketWrite(request, object, data);
            } else {
                try {
                    Response r = httpWrite(request, object, data).get(rootFuture.time(), rootFuture.timeUnit());
                    String m = r.getResponseBody();
                    if (m.length() > 0) {
                        TransportsUtil.invokeFunction(request.decoders(), functions, String.class, m, MESSAGE.name(), request.functionResolver());
                    }
                } catch (TimeoutException t) {
                    logger.trace("AHC Timeout", t);
                    rootFuture.timeoutException(t);
                } catch (Throwable t) {
                    logger.error("", t);
                }
            }
        }

        return rootFuture.finishOrThrowException();
    }

    public void webSocketWrite(Request request, Object object, Object data) throws IOException {
        WebSocketTransport webSocketTransport = WebSocketTransport.class.cast(transport);
        if (InputStream.class.isAssignableFrom(object.getClass())) {
            InputStream is = (InputStream) object;
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            //TODO: We need to stream directly, in AHC!
            byte[] buffer = new byte[8192];
            int n = 0;
            while (-1 != (n = is.read(buffer))) {
                bs.write(buffer, 0, n);
            }
            webSocketTransport.sendMessage(bs.toByteArray());
        } else if (Reader.class.isAssignableFrom(object.getClass())) {
            Reader is = (Reader) object;
            StringWriter bs = new StringWriter();
            //TODO: We need to stream directly, in AHC!
            char[] chars = new char[8192];
            int n = 0;
            while (-1 != (n = is.read(chars))) {
                bs.write(chars, 0, n);
            }
            webSocketTransport.sendMessage(bs.getBuffer().toString());
        } else if (String.class.isAssignableFrom(object.getClass())) {
            webSocketTransport.sendMessage(object.toString());
        } else if (byte[].class.isAssignableFrom(object.getClass())) {
            webSocketTransport.sendMessage((byte[]) object);
        } else {
            throw new IllegalStateException("No Encoder for " + data);
        }
    }

    public ListenableFuture<Response> httpWrite(Request request, Object object, Object data) throws IOException {

        BoundRequestBuilder b = configureAHC(request);

        if (InputStream.class.isAssignableFrom(object.getClass())) {
            //TODO: Allow reading the response.
            return b.setBody((InputStream) object).execute();
        } else if (Reader.class.isAssignableFrom(object.getClass())) {
            return b.setBody(new ReaderInputStream((Reader) object)).execute();
        } else if (String.class.isAssignableFrom(object.getClass())) {
            return b.setBody((String) object).execute();
        } else if (byte[].class.isAssignableFrom(object.getClass())) {
            return b.setBody((byte[]) object).execute();
        } else {
            throw new IllegalStateException("No Encoder for " + data);
        }
    }

    protected BoundRequestBuilder configureAHC(Request request) {
        FluentStringsMap m = DefaultSocket.decodeQueryString(request);

        return options.runtime().preparePost(request.uri())
                .setHeaders(request.headers())
                .setQueryParams(m)
                .setMethod(Request.METHOD.POST.name());
    }

}