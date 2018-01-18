package edu.cmu.ark.nlp.ace;

import java.io.File;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.cmu.ark.nlp.GeneralUtils;
import static edu.stanford.nlp.util.logging.Redwood.Util.log;

public class AcePreprocess {
	public static void go(String path1) throws IOException {
		String shortpath = edu.cmu.ark.nlp.analysis.Preprocess.shortPath(path1);
		
		shortpath = shortpath.replace("_APF.XML", "");
		String sgmlFilename = shortpath + ".SGM";
		assert new File(sgmlFilename).exists();
		String sgml = GeneralUtils.readFile(sgmlFilename);
		Pattern p = Pattern.compile("<TEXT>(.*)</TEXT>", Pattern.DOTALL);
		Matcher m = p.matcher(sgml);
		m.find();
		String text = m.group(1);
		GeneralUtils.writeFile(text, shortpath + ".txt");
	}
	
	public static void main(String args[]) throws IOException {
		for (String arg : args) {
			if (args.length > 1)  log("DOC\t%s\n", arg);
			go(arg);
		}
	}
}
