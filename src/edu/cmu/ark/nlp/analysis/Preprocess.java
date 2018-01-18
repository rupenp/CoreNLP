package edu.cmu.ark.nlp.analysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import edu.cmu.ark.nlp.GeneralUtils;
import edu.cmu.ark.nlp.parse.ArkParser;
import edu.cmu.ark.nlp.sent.SentenceBreaker;

public class Preprocess {
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		if(args.length == 0){
			System.err.println("You need to pass a text file as a command line argument.");
			System.exit(0);
		}
		String txtfile = args[0];
		Preprocess.go(txtfile);
	}

	public static boolean alreadyPreprocessed(String path) {
		String shortpath = shortPath(path);
		return 
			new File(shortpath+".sst").exists() &&
			new File(shortpath+".parse").exists();
	}

	public static String shortPath(String path) {
		return path.replace(".txt","").replace(".sent","");
	}
	
	public static void go(String path) throws IOException {
		go(path, false);
	}
	
	public static void writeOffsetSentenceFile(List <SentenceBreaker.Sentence> sentences, String shortpath, boolean useTempFiles) throws FileNotFoundException {
		File osentOutputFile = new File(shortpath + ".osent");
		if (useTempFiles) osentOutputFile.deleteOnExit();
		PrintWriter pwOSent = new PrintWriter(new FileOutputStream(osentOutputFile));

		for (SentenceBreaker.Sentence s : sentences) {
			pwOSent.printf("%d\t%d\t%s\n", s.charStart, s.charEnd, s.cleanText);
		}
		pwOSent.close();
	}
	
	public static void go(String path, boolean useTempFiles) throws IOException {
//		assert path.endsWith(".txt") || path.endsWith(".sent") : "bad filename extension";
		
		File parseOutputFile = new File(path+".parse");
		File sstOutputFile = new File(path+".sst");
		
		if (useTempFiles && !parseOutputFile.exists() && !sstOutputFile.exists()) {
			parseOutputFile.deleteOnExit();
			sstOutputFile.deleteOnExit();
		}
		
		PrintWriter pwParse = new PrintWriter(new FileOutputStream(parseOutputFile));
		PrintWriter pwSST = new PrintWriter(new FileOutputStream(sstOutputFile));
		
		String textpath;
		if (new File( (textpath=  path+".sent")).exists()) {
		} else if(new File(textpath= path+".txt").exists()) {
		} else { assert false : "need a sentence or text file"; }
		String text = GeneralUtils.readFile(textpath);
		String[] sentenceTexts = null;
		
		if (textpath.endsWith(".sent")) {
			sentenceTexts = text.split("\n");
		
		} else if (textpath.endsWith(".txt")) {
			List<SentenceBreaker.Sentence> sentences = ArkParser.cleanAndBreakSentences(text);
			writeOffsetSentenceFile(sentences, path, useTempFiles);
			
			sentenceTexts = new String[sentences.size()];
			for(int i=0; i < sentences.size(); i++) {
				sentenceTexts[i] = sentences.get(i).cleanText;
			}
		} else { assert false; }
		
		for(String sentence : sentenceTexts) {
			ArkParser.ParseResult res = ArkParser.getInstance().parseSentence(sentence);
			List<String> supersenses = ArkParser.getInstance().annotateSentenceWithSupersenses(res.parse);
			System.out.printf("%s\t%s\t%s\n", res.success ? "PARSE" : "ERROR", res.score, res.parse);
			pwParse.printf("%s\t%s\t%s\n", res.success ? "PARSE" : "ERROR", res.score, res.parse);
			for(int i=0; i < supersenses.size(); i++){
				String ss = supersenses.get(i);
				if(i>0) pwSST.print(" ");
				pwSST.print(res.parse.getLeaves().get(i) + "/" + ss.substring(ss.indexOf("-")+1));
			}
			pwSST.println();
		}
		pwSST.close();
		pwParse.close();
	}
}
