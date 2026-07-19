package org.mozilla.universalchardet;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

public class BasicStreamEncodingDetectionTest {
	@Test
	public void testUTF8 () throws IOException {
		Assert.assertEquals("UTF-8", getFileEncoding("utf8.txt"));
	}
	@Test
	public void testUTF8N () throws IOException {
		Assert.assertEquals("UTF-8", getFileEncoding("utf8n.txt"));
	}
	@Test
	public void testUTF16LE () throws IOException {
		Assert.assertEquals("UTF-16LE", getFileEncoding("utf16le.txt"));
	}
	@Test
	public void testShifJis () throws IOException {
		Assert.assertEquals("SHIFT_JIS", getFileEncoding("shiftjis.txt"));
	}
	
	@Test
	public void testEUC () throws IOException {
		Assert.assertEquals("EUC-JP", getFileEncoding("euc.txt"));
	}	
	@Test
	public void testISO2022JP () throws IOException {
		Assert.assertEquals("ISO-2022-JP", getFileEncoding("iso2022jp.txt"));
	}
	
	@Test
	public void testBIG5 () throws IOException {
		Assert.assertEquals("BIG5", getFileEncoding("big5.txt"));
	}
	
	@Test
	public void testEUCTW () throws IOException {
		Assert.assertEquals("EUC-TW", getFileEncoding("euctw.txt"));
	}
	@Test
	public void testEUCKR() throws IOException {
		Assert.assertEquals("EUC-KR", getFileEncoding("euckr.txt"));
	}
	@Test
	public void testWindows1255 () throws IOException {
		Assert.assertEquals("WINDOWS-1255", getFileEncoding("windows1255.txt"));
	}
	
	@Test
	public void testUTF8Emoji () throws IOException {
		Assert.assertEquals("UTF-8", getFileEncoding("utf8n-emoji.txt"));
	}
	
		 
	
	private String getFileEncoding(String testFileName) throws IOException{
        File file = new File("src/test/resources/" + testFileName);
        EncodingDetectorInputStream edis = null;
        EncodingDetectorOutputStream edos = null;
        try {
        	edis = new EncodingDetectorInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())));        
	        edos = new EncodingDetectorOutputStream(NullOutputStream.NULL_OUTPUT_STREAM);
	        byte[] buffer = new byte[1024];
	        int read = 0;
	        while (( read = edis.read(buffer)) > 0){
	        	edos.write(buffer, 0, read);
	        }
        }
        finally {
        	edos.close();
        	edis.close();
        }
        String encodingRead = edis.getDetectedCharset();
        String encodingWrite = edos.getDetectedCharset();
        Assert.assertNotNull(encodingRead);
        Assert.assertNotNull(encodingWrite);
        Assert.assertTrue(encodingRead.equals(encodingWrite));
        return encodingRead;
        
	}
}
