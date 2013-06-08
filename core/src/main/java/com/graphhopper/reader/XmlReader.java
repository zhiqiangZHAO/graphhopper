package com.graphhopper.reader;

import com.graphhopper.reader.pbf.PbfDecoder;
import com.graphhopper.reader.pbf.PbfStreamSplitter;
import com.graphhopper.reader.pbf.Sink;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: Nop
 * Date: 08.06.13
 * Time: 09:58
 * To change this template use File | Settings | File Templates.
 */
public class XmlReader implements Runnable
{
    private InputStream inputStream;
    private Sink sink;

    private XMLStreamReader parser;


    public XmlReader(InputStream in, Sink sink ) {
        this.inputStream = in;
        this.sink = sink;
    }

    public void run() {
        try {

            openXMLStream();

            int event = parser.next();
            while (event != XMLStreamConstants.END_DOCUMENT) {
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = parser.getLocalName();
                    long id = 0;
                    switch (name.charAt(0)) {
                        case 'n':
                            id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            sink.process( new OSMNode(id, parser));
                            break;

                        case 'w':
                            id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            sink.process(  new OSMWay(id, parser) );
                            break;

                        case 'r':
                            id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            sink.process(  new OSMRelation(id, parser) );
                            break;
                    }
                }
                event = parser.next();
            }
            parser.close();
            sink.complete();

        } catch (Exception e) {
            throw new RuntimeException("Unable to read XML file.", e);
        } finally {
        }
    }

    private void openXMLStream()
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        parser = factory.createXMLStreamReader(inputStream, "UTF-8");

        int event = parser.next();
        if (event != XMLStreamConstants.START_ELEMENT || !parser.getLocalName().equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("File is not a valid OSM stream");
        }
    }

}
