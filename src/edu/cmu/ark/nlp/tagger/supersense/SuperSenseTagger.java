package edu.cmu.ark.nlp.tagger.supersense;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.cmu.ark.nlp.question.QuestionUtil;
import edu.stanford.nlp.trees.Tree;

public class SuperSenseTagger {

	private DiscriminativeTagger sst;

	public SuperSenseTagger(Properties props) {
		this.sst = new DiscriminativeTagger(props);
	}

	public List<String> annotateSentenceWithSupersenses(Tree sentence) {
		List<String> result = new ArrayList<String>();

		int numleaves = sentence.getLeaves().size();
		if(numleaves <= 1){
			return result;
		}
		LabeledSentence labeled = generateSupersenseTaggingInput(sentence);

		try {
			sst.findBestLabelSequenceViterbi(labeled, sst.getWeights());
			for(String pred: labeled.getPredictions()){
				result.add(pred);
			}
		} catch (Exception e){
			e.printStackTrace();
		}

		//add a bunch of blanks if necessary
		while(result.size() < numleaves) result.add("0");

		return result;
	}

	private LabeledSentence generateSupersenseTaggingInput(Tree sentence){
		LabeledSentence res = new LabeledSentence();
		List<Tree> leaves = sentence.getLeaves();

		for(int i=0;i<leaves.size();i++){
			String word = leaves.get(i).label().toString();
			Tree preterm = leaves.get(i).parent(sentence);
			String pos = preterm.label().toString();
			String stem = QuestionUtil.getLemma(word, pos);
			res.addToken(word, stem, pos, "0");
		}
		return res;
	}


}
