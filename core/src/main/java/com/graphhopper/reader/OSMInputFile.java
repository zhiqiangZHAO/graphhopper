/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.reader.pbf.Sink;
import com.graphhopper.reader.pbf.PbfReader;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * A readable OSM file returning objects ala OSMElement.
 *
 * @author Nop
 */
public class OSMInputFile implements Sink {

    private InputStream bis;
    private boolean eof;
    // false for xml parsing, true for pbf parsing
    private boolean binary = false;
    private final BlockingQueue<OSMElement> itemQueue;
    private transient boolean hasIncomingData;
    private int workerThreads = -1;

    public OSMInputFile(File file) throws IOException {
        bis = decode(file);
        itemQueue = new LinkedBlockingQueue<OSMElement>(50000);
    }

    public OSMInputFile open() {
        hasIncomingData = true;
        eof = false;
        if (binary) {
            if (workerThreads <= 0)
                workerThreads = 2;
            PbfReader reader = new PbfReader(bis, this, workerThreads);
            new Thread(reader, "PBF Reader").start();
        } else {
            XmlReader reader = new XmlReader(bis, this);
            new Thread(reader, "Xml Reader").start();
        }
        return this;
    }

    /**
     * Currently on for pbf format. Default is number of cores.
     */
    public OSMInputFile workerThreads(int num) {
        workerThreads = num;
        return this;
    }

    // should we move this to Helper?
    private InputStream decode(File file) throws IOException {
        final String name = file.getName();

        InputStream ips = null;
        try {
            ips = new BufferedInputStream(new FileInputStream(file), 50000);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ips.mark(10);

        // check file header
        byte header[] = new byte[6];
        ips.read(header);

        /*     can parse bz2 directly with additional lib
         if (header[0] == 'B' && header[1] == 'Z')
         {
         return new CBZip2InputStream(ips);
         }
         */
        if (header[0] == 31 && header[1] == -117) {
            ips.reset();
            return new GZIPInputStream(ips, 50000);
        } else if (header[0] == 0 && header[1] == 0 && header[2] == 0
                && header[3] == 13 && header[4] == 10 && header[5] == 9) {
            ips.reset();
            binary = true;
            return ips;
        } else if (header[0] == 'P' && header[1] == 'K') {
            ips.reset();
            ZipInputStream zip = new ZipInputStream(ips);
            zip.getNextEntry();

            return zip;
        } else if (name.endsWith(".osm") || name.endsWith(".xml")) {
            ips.reset();
            return ips;
        } else {
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
        }
    }
    
    public OSMElement getNext() throws XMLStreamException {
        if (eof)
            throw new IllegalStateException("EOF reached");

        OSMElement next = null;
        while (next == null) {
            // we are done, stop waiting
            if (!hasIncomingData && itemQueue.isEmpty()) {
                eof = true;
                break;
            }

            // we cannot block via itemQueue.take as hasIncomingData can change
            try {
                next = itemQueue.poll(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                break;
            }
        }
        return next;
    }

    public boolean eof() {
        return eof;
    }

    public void close() throws XMLStreamException, IOException {
        eof = true;
        bis.close();
    }

    @Override
    public void process(OSMElement item) {
        try {
            itemQueue.put(item);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void complete() {
        hasIncomingData = false;
    }
}