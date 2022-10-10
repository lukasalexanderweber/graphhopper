package com.graphhopper.reader.osm;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

import javax.xml.stream.XMLStreamException;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;

public interface OSMParseInterface {
	enum State {
	    Initial,
	    Header,
	    Node,
	    Way,
	    Relation,
	    Error;
	    static public final Integer length = 1 + Error.ordinal();
	}
	
	State transition[][] = {
		    //  FILEHEADER      NODE            WAY            RELATION      // | Events
		    {
		        State.Header,   State.Node,     State.Error,   State.Error   // | Initial (file can start with Header or Node)	
		    }, {
		    	State.Error,    State.Node,     State.Error,   State.Error   // | Header (can only happen once)
		    }, {
		    	State.Error,    State.Node,     State.Way,     State.Error   // | Node	
		    }, {
		    	State.Error,    State.Error,    State.Way,     State.Relation// | Way
		    }, {
		    	State.Error,    State.Error,    State.Error,   State.Relation// | Relation
		    }
		};
	
    default void readOSM(File file, int workerThreads) {
    	State state = State.Initial;
    	State new_state = null;
        try (OSMInput osmInput = openOsmInputFile(file, workerThreads)) {
            ReaderElement elem;
            ReaderElement.Type type;
            while ((elem = osmInput.getNext()) != null)
            	if (elem != null) {
                	type = elem.getType();
                	new_state = transition[state.ordinal()][type.ordinal()];
                	if (new_state == State.Error) {
                		throw new IllegalStateException("Invalid OSM File: " + type + " after " + state);
                	}
                	state = new_state;
                	handleElement(elem, type);                	
            	}
            onFinish();
            if (osmInput.getUnprocessedElements() > 0)
                throw new IllegalStateException("There were some remaining elements in the reader queue " + osmInput.getUnprocessedElements());
        } catch (Exception e) {
            throw new RuntimeException("Could not parse OSM file: " + file.getAbsolutePath(), e);
        }
    }

    default OSMInput openOsmInputFile(File osmFile, int workerThreads) throws XMLStreamException, IOException {
        return new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();
    }

	
    default void handleElement(ReaderElement elem, ReaderElement.Type type) throws ParseException {
        switch (type) {            
            case FILEHEADER:
                handleFileHeader((OSMFileHeader) elem);
                break;
            case NODE:
                handleNode((ReaderNode) elem);
                break;
            case WAY:
                handleWay((ReaderWay) elem);
                break;
            case RELATION:
                handleRelation((ReaderRelation) elem);
                break;
        }
    }

    default void handleNode(ReaderNode node) {
    }

    default void handleWay(ReaderWay way) {
    }

    default void handleRelation(ReaderRelation relation) {
    }

    default void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
    }

    default void onFinish() {
    }
}