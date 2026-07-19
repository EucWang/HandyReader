package org.mozilla.universalchardet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;



public class BOMTest {

	private static final String TEST_STRING = 
			"========================================================================";
	
	@Test
	public void testUTF8 () throws IOException {
		Assert.assertEquals(TEST_STRING, getFirstLine("utf8.txt"));
	}
	@Test
	public void testUTF8N () throws IOException {
		Assert.assertEquals(TEST_STRING, getFirstLine("utf8n.txt"));
	}
	@Test
	public void testUTF16LE () throws IOException {
		Assert.assertEquals(TEST_STRING, getFirstLine("utf16le.txt"));
	}
	
	private String getFirstLine(String testFileName) throws IOException{
        String fileName = "src/test/resources/" + testFileName;
        File file = new File(fileName);
        BufferedReader reader = null;
        try {
        	reader = ReaderFactory.createBufferedReader(file);
        	// return first line
        	return reader.readLine();
        }
        finally {
        	if (reader != null) {
        		reader.close();
        	}
        }
	}
}
