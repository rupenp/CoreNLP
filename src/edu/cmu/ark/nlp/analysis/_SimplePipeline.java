package edu.cmu.ark.nlp.analysis;

import java.io.IOException;

import edu.cmu.ark.nlp.data.Document;



public class _SimplePipeline {

	public static void main(String[] args) throws IOException {
		for (String path : args) {
			System.out.printf("***  Input %s  ***\n", path);
			_SimplePipeline.go(Document.loadFiles(path));
		}	
	}
	
	public static void go(Document d) throws IOException{
		FindMentions.go(d);
		Resolve.go(d, false);
		RefsToEntities.go(d);
	}
	
}