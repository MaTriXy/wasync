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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.atmosphere.wasync.Decoder;
import org.atmosphere.wasync.Event;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.decoder.PaddingAndHeartbeatDecoder;
import org.atmosphere.wasync.decoder.TrackMessageSizeDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A specialized {@link org.atmosphere.wasync.Request} implementation to use with the Atmosphere Framework. Functionality
 * like track message length, broadcaster cache, etc. can be configured using this object. Make sure your server
 * is properly configured before changing the default.
 * <p>
 * AtmosphereRequest MUST NOT be shared between {@link org.atmosphere.wasync.Socket} instance because they hold information about the
 * Atmosphere Protocol like the UUID.
 *
 * @author Jeanfrancois Arcand
 */
public class AtmosphereRequest extends DefaultRequest<AtmosphereRequest.AtmosphereRequestBuilder> {

    public enum CACHE {HEADER_BROADCAST_CACHE, UUID_BROADCASTER_CACHE, SESSION_BROADCAST_CACHE, NO_BROADCAST_CACHE}

    private final static Logger logger = LoggerFactory.getLogger(AtmosphereRequest.class);

    protected AtmosphereRequest(AtmosphereRequestBuilder builder) {
        super(builder);
    }

    /**
     * Return the {@link AtmosphereRequest.CACHE} used. The value must match the Atmosphere's Broadcaster cache implementation
     * of the server.
     *
     * @return the {@link AtmosphereRequest.CACHE}
     */
    public AtmosphereRequest.CACHE getCacheType() {
        return builder.getCacheType();
    }

    /**
     * Is tracking message's length enabled.
     *
     * @return true if enabled
     */
    public boolean isTrackMessageLength() {
        return builder.trackMessageLength;
    }

    /**
     * True if the Atmosphere Protocol is enabled
     *
     * @return true if the Atmosphere Protocol is enabled.
     */
    public boolean enableProtocol() {
        return builder.enableProtocol;
    }

    /**
     * The delimiter used by the Atmosphere Framework when sending message length and message's size.
     *
     * @return delimiter used. Default is '|'
     */
    public String getTrackMessageLengthDelimiter() {
        return builder.trackMessageLengthDelimiter;
    }

    /**
     * The padding size sent by Atmosphere
     *
     * @return padding size. Default is 4098
     */
    public int getPaddingSize() {
        return builder.paddingSize;
    }

    /**
     * A builder for {@link AtmosphereRequest}. This builder configure the Atmosphere Protocol on the request object.
     */
    public static class AtmosphereRequestBuilder extends RequestBuilder<AtmosphereRequestBuilder> {

        private CACHE cacheType = CACHE.NO_BROADCAST_CACHE;
        private boolean trackMessageLength = false;
        private String trackMessageLengthDelimiter = "|";
        private int paddingSize = 4098;
        private boolean enableProtocol = true;
        private final BDecoder bDecoder = new BDecoder();
        private final SDecoder sDecoder = new SDecoder();

        public AtmosphereRequestBuilder() {
            super(AtmosphereRequestBuilder.class);
        }

        /**
         * Return the {@link AtmosphereRequest.CACHE} used. The value must match the Atmosphere's Broadcaster cache implementation
         * of the server.
         *
         * @return the {@link AtmosphereRequest.CACHE}
         */
        private CACHE getCacheType() {
            return cacheType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public AtmosphereRequestBuilder transport(TRANSPORT t) {
            if (queryString.get("X-Atmosphere-Transport") == null) {
                List<String> l = new ArrayList<String>();
                if (t.equals(TRANSPORT.LONG_POLLING)) {
                    l.add("long-polling");
                } else {
                    l.add(t.name().toLowerCase());
                }

                queryString.put("X-Atmosphere-Transport", l);
            }
            transports.add(t);
            return derived.cast(this);
        }

        /**
         * Set the {@link CACHE} used by the server side implementation of Atmosphere.
         *
         * @param c the cache type.
         * @return this;
         */
        public AtmosphereRequestBuilder cache(CACHE c) {
            this.cacheType = c;
            return this;
        }

        /**
         * Turn on/off tracking message.
         *
         * @param trackMessageLength true to enable.
         * @return this
         */
        public AtmosphereRequestBuilder trackMessageLength(boolean trackMessageLength) {
            this.trackMessageLength = trackMessageLength;
            return this;
        }

        /**
         * Set the tracking delimiter.
         *
         * @param trackMessageLengthDelimiter true to enable.
         * @return this
         */
        public AtmosphereRequestBuilder trackMessageLengthDelimiter(String trackMessageLengthDelimiter) {
            this.trackMessageLengthDelimiter = trackMessageLengthDelimiter;
            return this;
        }

        /**
         * Set to true to enable the Atmosphere Protocol. Default is true.
         *
         * @param enableProtocol false to disable.
         * @return this
         */
        public AtmosphereRequestBuilder enableProtocol(boolean enableProtocol) {
            this.enableProtocol = enableProtocol;
            return this;
        }

        /**
         * Set the size of the padding bytes or String send by Atmosphere's PaddingAtmosphereInterceptor
         *
         * @param paddingSize false to disable.
         * @return this
         */
        public AtmosphereRequestBuilder paddingSize(int paddingSize) {
            this.paddingSize = paddingSize;
            return this;
        }

        /**
         * {@inheritDoc}
         * Important: You cannot call the build() method more than once if {@link #enableProtocol} or {@link #trackMessageLength}
         * are set to true.
         */
        @Override
        public AtmosphereRequest build() {
            if (enableProtocol) {
                List<String> l = new ArrayList<String>();
                l.add("2.3.0");
                queryString.put("X-Atmosphere-Framework", l);

                l = new ArrayList<String>();
                l.add("0");
                queryString.put("X-Atmosphere-tracking-id", l);

                l = new ArrayList<String>();
                l.add("true");
                queryString.put("X-atmo-protocol", l);

                Collection ct = headers().getAll("Content-Type");
                if (ct != null && ct.size() > 0) {
                    l = new ArrayList<String>();
                    l.addAll(ct);
                    queryString.put("Content-Type", l);
                }

                _addDecoder(0, sDecoder);
                _addDecoder(0, bDecoder);
            }

            if (trackMessageLength) {                
                List<String> l = new ArrayList<String>();
                l.add("true");
                queryString.put("X-Atmosphere-TrackMessageSize", l);
                
                TrackMessageSizeDecoder trackMessageSizeDecoder;
                if (trackMessageLengthDelimiter.length() > 0) {
                    trackMessageSizeDecoder = new TrackMessageSizeDecoder(trackMessageLengthDelimiter, enableProtocol);
                } else {
                    trackMessageSizeDecoder = new TrackMessageSizeDecoder(enableProtocol);
                }
                _addDecoder(0, trackMessageSizeDecoder);
            }

            return new AtmosphereRequest(this);
        }

        private void _addDecoder(int pos, Decoder decoder) {
            if (!decoders.contains(decoder)) {
                decoders.add(pos, decoder);
            }
        }

        private void _addDecoder(Decoder decoder) {
            if (!decoders.contains(decoder)) {
                decoders.add(decoder);
            }
        }

        private final void handleProtocol(String s) {
            String[] proto = s.trim().split("\\|");
            // Track message size may have been appended
            int pos = trackMessageLength ? 1 : 0;

            List<String> l = new ArrayList<String>();
            l.add(proto[pos]);
            queryString.put("X-Atmosphere-tracking-id", l);

            String heartbeatChar = "X";
            if (proto.length == 3) {
                heartbeatChar = proto[2];
            }
            _addDecoder(2,new PaddingAndHeartbeatDecoder(paddingSize, heartbeatChar));
        }

        private final class SDecoder implements Decoder<String, Decoder.Decoded<String>> {

            private AtomicBoolean protocolReceived = new AtomicBoolean();

            /**
             * Handle the Atmosphere's Protocol.
             */
            @Override
            public Decoder.Decoded<String> decode(Event e, String s) {
                if (e.equals(Event.MESSAGE) && !protocolReceived.getAndSet(true)) {
                    try {
                        handleProtocol(s);
                        decoders.remove(this);
                        decoders.remove(bDecoder);

                        return Decoder.Decoded.ABORT;
                    } catch (Exception ex) {
                        logger.warn("Unable to decode the protocol {}", s);
                        logger.warn("", e);
                    }
                }
                return new Decoder.Decoded<String>(s);
            }
        }

        private final class BDecoder implements Decoder<byte[], Decoder.Decoded<byte[]>> {

            private AtomicBoolean protocolReceived = new AtomicBoolean();

            /**
             * Handle the Atmosphere's Protocol.
             */
            @Override
            public Decoder.Decoded<byte[]> decode(Event e, byte[] b) {
                if (e.equals(Event.MESSAGE) && !protocolReceived.getAndSet(true)) {
                    try {
                        handleProtocol(new String(b, "UTF-8"));
                        decoders.remove(this);
                        decoders.remove(sDecoder);

                        return Decoder.Decoded.ABORT;
                    } catch (Exception ex) {
                        logger.warn("Unable to decode the protocol {}", new String(b));
                        logger.warn("", e);
                    }
                }
                return new Decoder.Decoded<byte[]>(b);
            }
        }
    }
}
