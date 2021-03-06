/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.atlas.web;

import java.io.IOException;
import java.net.ServerSocket;

public class WebLib
{
    /** Split a string, removing whitespace around the split string.
     * e.g. Use in splitting HTTP accept/content-type headers.
     */
    public static String[] split(String s, String splitStr)
    {
        String[] x = s.split(splitStr,2) ;
        for ( int i = 0 ; i < x.length ; i++ )
        {
            x[i] = x[i].trim() ;
        }
        return x ;
    }

    /**
     * Choose an unused port for a server to listen on.
     * Note: Fuseki main will start of "port 0", and then return the port actually allocated.
     * This is atomic whereas "choosePort" does not reserve the port.
     */
    public static int choosePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to find a port");
        }
    }

}
