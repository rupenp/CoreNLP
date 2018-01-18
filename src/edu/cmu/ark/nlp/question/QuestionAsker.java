// Question Generation via Overgenerating Transformations and Ranking
// Copyright (c) 2008, 2009 Carnegie Mellon University.  All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Michael Heilman
//	  Carnegie Mellon University
//	  mheilman@cmu.edu
//	  http://www.cs.cmu.edu/~mheilman
package edu.cmu.ark.nlp.question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.cmu.ark.nlp.parse.ArkParser;
import edu.cmu.ark.nlp.tagger.supersense.SuperSenseTagger;
import edu.stanford.nlp.trees.Tree;

/**
 * Wrapper class for outputting a (ranked) list of questions given an entire document,
 * not just a sentence.  It wraps the three stages discussed in the technical report and calls each in turn 
 * (along with parsing and other preprocessing) to produce questions.
 * 
 * This is the typical class to use for running the system via the command line. 
 * 
 * Example usage:
 * 
    java -server -Xmx800m -cp lib/weka-3-6.jar:lib/stanford-parser-2008-10-26.jar:bin:lib/jwnl.jar:lib/commons-logging.jar:lib/commons-lang-2.4.jar:lib/supersense-tagger.jar:lib/stanford-ner-2008-05-07.jar:lib/arkref.jar \
	edu/cmu/ark/QuestionAsker \
	--verbose --simplify --group \
	--model models/linear-regression-ranker-06-24-2010.ser.gz \
	--prefer-wh --max-length 30 --downweight-pro
 * 
 * @author mheilman@cs.cmu.edu
 *
 */
public class QuestionAsker {
	private QuestionTransducer qt;
	private InitialTransformationStep trans;
	//private Properties props;
	private QuestionRanker qr;
	private ArkParser parser;
	
	private boolean doStemming;
	private boolean avoidFreqWords;
	private boolean preferWH;
	private boolean downweightPronouns;
	private boolean justWH;

	public QuestionAsker(Properties props){
		//this.props = props;

		SuperSenseTagger sst = new SuperSenseTagger(props);

		qt = new QuestionTransducer(props, sst);
		qt.setAvoidPronounsAndDemonstratives(new Boolean(props.getProperty("dropPronouns", "true")));

		NPClarification npc = new NPClarification(sst);
		trans = new InitialTransformationStep(props, npc);
		
		trans.setDoPronounNPC(new Boolean(props.getProperty("doPronounNPC", "true")));
		trans.setDoNonPronounNPC(new Boolean(props.getProperty("doNonPronounNPC", "false")));

		qr = new QuestionRanker(props);
		parser = new ArkParser(props);
		
		doStemming = new Boolean(props.getProperty("doStemming", "true"));
		avoidFreqWords = new Boolean(props.getProperty("avoidFreqWords", "false"));
		preferWH = new Boolean(props.getProperty("preferWH", "true"));
		downweightPronouns = new Boolean(props.getProperty("downweightPronouns", "true"));
		justWH = new Boolean(props.getProperty("justWH", "false"));
	}

	public List<Question> ask(String text) throws IOException 
	{
		List<String> sentences = QuestionUtil.getSentences(text);
		List<Question> outputQuestionList = new ArrayList<Question>();
		List<Question> finalQuestionAnswers = new ArrayList<Question>();
		//iterate over each segmented sentence and generate questions
		List<Tree> inputTrees = new ArrayList<Tree>();
		Tree parsed;
		for(String sentence: sentences){
			parsed = this.parser.parseSentence(sentence).parse;
			inputTrees.add(parsed);
		}

		//step 1 transformations
		List<Question> transformationOutput = trans.transform(inputTrees);
		//step 2 question transducer
		for(Question t: transformationOutput){
			qt.generateQuestionsFromParse(t);
			outputQuestionList.addAll(qt.getQuestions());
		}

		QuestionTransducer.removeDuplicateQuestions(outputQuestionList);

		//step 3 ranking
		qr.scoreGivenQuestions(outputQuestionList);
		
		qr.adjustScores(outputQuestionList, inputTrees,
				avoidFreqWords, preferWH, downweightPronouns, doStemming);
		QuestionRanker.sortQuestions(outputQuestionList, false);
		
		for(Question question: outputQuestionList){
			//if(question.getTree().getLeaves().size() > maxLength){
			//	continue;
			//}
			if(this.justWH && question.getFeatureValue("whQuestion") != 1.0){
				continue;
			}
			finalQuestionAnswers.add(question);
		}
		return finalQuestionAnswers;
	}
}