package com.graphhopper.reader.osm;

import static com.graphhopper.util.Helper.nf;

import org.slf4j.Logger;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.util.Helper;

public class ElementHandler {
    protected long counter = 0;
    private ReaderElement.Type type;
    
    public ElementHandler(ReaderElement.Type type) {
        this.type = type;
    }
    
    public void onStart() {
    }
    
    public void handleElement(ReaderElement elem) {
    }
    
    public void onFinish() {
    }
    
    protected void logEvery(Logger LOGGER, long frequency) {
        if (counter % frequency == 0)
            LOGGER.info("processed " + type.toString().toLowerCase() + "s: " + nf(counter) + " " + Helper.getMemInfo());
    }
}