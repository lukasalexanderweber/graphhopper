package com.graphhopper.reader.osm;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import javax.xml.stream.XMLStreamException;

import com.graphhopper.reader.ReaderElement;

public class OSMParser {
    private ElementHandler fileheaderHandler = new ElementHandler(ReaderElement.Type.FILEHEADER);
    private ElementHandler nodeHandler = new ElementHandler(ReaderElement.Type.NODE);
    private ElementHandler wayHandler = new ElementHandler(ReaderElement.Type.WAY);
    private ElementHandler relationHandler = new ElementHandler(ReaderElement.Type.RELATION);
    
    private ElementHandler activeHandler = null;
    
    public OSMParser setFileheaderHandler(FileHeaderHandler fileheaderHandler) {
        this.fileheaderHandler = fileheaderHandler;
        return this;
    }
    
    public OSMParser setNodeHandler(NodeHandler nodeHandler) {
        this.nodeHandler = nodeHandler;
        return this;
    }

    public OSMParser setWayHandler(WayHandlerBase wayHandler) {
        this.wayHandler = wayHandler;
        return this;
    }

    public OSMParser setRelationHandler(RelationHandlerBase relationHandler) {
        this.relationHandler = relationHandler;
        return this;
    }
    
	enum State {
	    Initial,
	    Header,
	    Node,
	    Way,
	    Relation,
	    Error;
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
	
    public void readOSM(File file, int workerThreads) {
    	State state = State.Initial;
    	State new_state = null;
        try (OSMInput osmInput = openOsmInputFile(file, workerThreads)) {
            ReaderElement elem;
            ReaderElement.Type type;
            while ((elem = osmInput.getNext()) != null)
            	if (elem != null) { // TODO why can elem be null at this place?
                	type = elem.getType();
                	
                	if (!(state.ordinal()-1 == type.ordinal())) {
                	    
                	    // a transition has happened!
                        new_state = transition[state.ordinal()][type.ordinal()];
                        if (new_state == State.Error) {
                            throw new IllegalStateException("Invalid OSM File: " + type + " after " + state);
                        }
                        
                        if (activeHandler != null) {
                            activeHandler.onFinish();
                        }
                        setActiveHandler(type);
                        activeHandler.onStart();
                        
                        state = new_state;
                	}
                	activeHandler.handleElement(elem);
            	}
            activeHandler.onFinish();
            if (osmInput.getUnprocessedElements() > 0) // TODO a smoother solution would be to expect a file footer
                throw new IllegalStateException("There were some remaining elements in the reader queue " + osmInput.getUnprocessedElements());
        } catch (Exception e) {
            throw new RuntimeException("Could not parse OSM file: " + file.getAbsolutePath(), e);
        }
    }

    private OSMInput openOsmInputFile(File osmFile, int workerThreads) throws XMLStreamException, IOException {
        return new OSMInputFile(osmFile).setWorkerThreads(workerThreads).open();
    }
    
    private void setActiveHandler(ReaderElement.Type type) {
        switch (type) {            
            case FILEHEADER:
                activeHandler = fileheaderHandler;
                break;
            case NODE:
                activeHandler = nodeHandler;
                break;
            case WAY:
                activeHandler = wayHandler;
                break;
            case RELATION:
                activeHandler = relationHandler;
                break;
        }
    }
    
    public Date getTimeStamp() {
        if (fileheaderHandler.getClass() == FileHeaderHandler.class)
            return ((FileHeaderHandler) fileheaderHandler).getTimeStamp();
        return null;
    }
}