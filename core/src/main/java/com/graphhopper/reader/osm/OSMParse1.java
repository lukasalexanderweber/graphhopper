package com.graphhopper.reader.osm;

import java.text.ParseException;
import java.util.Date;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.Helper;

public class OSMParse1 implements OSMParseInterface {
	private WayPreprocessor wayPreprocessor;
	private RelationPreprocessor relationPreprocessor;
	private Date timestamp;
	
	public OSMParse1(WayPreprocessor wayPreprocessor, RelationPreprocessor relationPreprocessor) {
		this.wayPreprocessor = wayPreprocessor;
		this.relationPreprocessor = relationPreprocessor;
	}

    @Override
    public void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
        timestamp = Helper.createFormatter().parse(fileHeader.getTag("timestamp"));
    }

    @Override
    public void handleWay(ReaderWay way) {
    	wayPreprocessor.preprocessWay(way);
    }
    
    @Override
    public void handleRelation(ReaderRelation relation) {
    	relationPreprocessor.preprocessRelation(relation);
    }
    
    public Date getTimeStamp() {
        return timestamp;
    }
}
