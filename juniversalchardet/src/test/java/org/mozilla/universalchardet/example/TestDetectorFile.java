package org.mozilla.universalchardet.example;

import org.mozilla.universalchardet.UniversalDetector;

public class TestDetectorFile {

	public static void main (String[] args) throws java.io.IOException {
		if (args.length != 1) {
			System.err.println("Usage: java TestDetectorFile FILENAME");
			System.exit(1);
		}
		java.io.File file = new java.io.File(args[0]);
		String encoding = UniversalDetector.detectCharset(file);
		if (encoding != null) {
			System.out.println("Detected encoding = " + encoding);
		} else {
			System.out.println("No encoding detected.");
		}
	}
}
