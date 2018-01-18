package edu.cmu.ark.nlp.sent;

import java.io.FileNotFoundException;

import edu.cmu.ark.nlp.GeneralUtils;
import edu.cmu.ark.nlp.parse.ArkParser;



public class StanfordSent {
	public static void main(String[] args) throws FileNotFoundException {
		String text = GeneralUtils.readFile(args[0]);
		for(String s : ArkParser.getInstance().getSentencesStanford(text)) {
			System.out.println(s);
		}
	}
}
