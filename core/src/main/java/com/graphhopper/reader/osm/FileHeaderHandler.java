package com.graphhopper.reader.osm;

import java.text.ParseException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.util.Helper;

public class FileHeaderHandler extends ElementHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileHeaderHandler.class);
    private Date timestamp = null;
    
    public FileHeaderHandler() {
        super(ReaderElement.Type.FILEHEADER);
    }
        
    @Override
    public void handleElement(ReaderElement elem) {
        handleFileHeader((OSMFileHeader) elem);
    }
        
    private void handleFileHeader(OSMFileHeader fileHeader) {
        try {
            timestamp = Helper.createFormatter().parse(fileHeader.getTag("timestamp"));
        } catch (ParseException e) {
            LOGGER.error("Error parsing timestamp " + e);
        }
    }

    public Date getTimeStamp() {
        return timestamp;
    }
}
