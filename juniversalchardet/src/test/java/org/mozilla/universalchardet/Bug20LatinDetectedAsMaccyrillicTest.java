package org.mozilla.universalchardet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class Bug20LatinDetectedAsMaccyrillicTest {
	
	private static final String TEST_STRING = "ÄÜÖßäöü,Name1ÄÜÖßäöü,Name2ÄÜÖßäöü,Name3ÄÜÖßäöü,StreetÄÜÖßäöü,MÄÜÖßäöü,DE,80080,München,ContactÄÜÖßäöü,+49(0)ÄÜÖßäöü,ÄÜÖßäöü@gls-itservices.com,CommentÄÜÖßäöü,+49,(0)98,765,432,BlÄÜÖßäöü";
	
	
	
	@Test
	@Ignore("Bug not fixed yet")
	public void testFile () throws IOException {
		File testFile = new File("src/test/resources/bug20-example-latin.txt");
		String originalEncoding = UniversalDetector.detectCharset(testFile);
		Assert.assertEquals("WINDOWS-1252", originalEncoding);
	}
	
	@Test
	@Ignore("Bug not fixed yet")
	public void testLatin() throws IOException {

		UniversalDetector detector = new UniversalDetector();
		detector.handleData(TEST_STRING.getBytes(Charset.forName("WINDOWS-1252")));
		detector.dataEnd();
		Assert.assertEquals("WINDOWS-1252", detector.getDetectedCharset());
		
	}
	
	
	@Test
	public void testUTF8()  {
		UniversalDetector detector = new UniversalDetector();
		detector.handleData(TEST_STRING.getBytes(Charset.forName("UTF-8")));
		detector.dataEnd();
		Assert.assertEquals("UTF-8", detector.getDetectedCharset());
		
	}

}
