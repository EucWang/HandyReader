package org.mozilla.universalchardet;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class TIS620BasicTest {

	@Test
	public void testTIS620() throws IOException {
		Assert.assertEquals("TIS620", getFileEncoding("tis620.txt"));
	}
	
	
	
	private String getFileEncoding(String testFileName) throws IOException{
        String fileName = "src/test/resources/" + testFileName;
        return UniversalDetector.detectCharset(new File(fileName));
	}
}
