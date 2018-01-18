package edu.cmu.ark.nlp.analysis;

import java.util.ArrayList;

import edu.cmu.ark.nlp.data.Document;
import edu.cmu.ark.nlp.data.Mention;
import edu.cmu.ark.nlp.data.Sentence;
import edu.cmu.ark.nlp.parse.ArkParser;
import edu.cmu.ark.nlp.parse.TregexPatternFactory;

import com.aliasi.util.Strings;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

public class Resolve {
	public static void go(Document d, Boolean oracleSemantics) {
		System.out.println("\n***  Resolve ***\n");
		Mention antecedent;
		Sentence curS = null;
		for (Mention m : d.mentions()) {
			if (m.getSentence() != curS) {
				curS = m.getSentence();
				System.out.printf("\n== S%-2s  %s\n", curS.ID(), curS.text());
			}
			System.out.println("\n= Resolving\t" + m);
			if (m.node()==null) {
				System.out.println("No parse node, skipping");
				continue;
			}
			if (Types.isPronominal(m)) {
				resolvePronoun(m, d);
			//} else if (isRelativePronoun(m)){
			//	resolveRelativePronoun(m, d);
			} else if (inAppositiveConstruction(m)) {
				resolveAppositive(m, d);
			} else if ((antecedent = findAntecedentInRoleAppositiveConstruction(m,d)) != null) {
				d.refGraph().setRef(m, antecedent);
				reportResolution("role-appos", m, antecedent);
			} else if ((antecedent = findAntecendentInPredicateNominativeConstruction(m, d)) != null) {
				d.refGraph().setRef(m, antecedent);
				reportResolution("pred-nom", m, antecedent);
			} else {
				resolveOther(m, d, oracleSemantics);
			}
			
			
			if (d.refGraph().getFinalResolutions().get(m) == null) {
				boolean hadAChance = isThereAGoldAntecedent(d, m);
				reportResolution("null", m, null, hadAChance);
				if (hadAChance && m.aceMention!=null && !Types.isPronominal(m)) {
					System.out.printf("%s   gold antecedent candidates:\n", m.aceMention);
					printGoldAntecedents(d,m);
				}
			}
		}
	}
	
	public static void reportResolution(String reason, Mention mention, Mention ref) {
		reportResolution(reason,mention,ref, true);
	}
	/** really anal-retentive output format to enable grep-based statistical analysis **/
	public static void reportResolution(String reason, Mention mention, Mention ref, boolean hadAChance) {
		String eval = null;
		if (mention.aceMention!=null && ref==null) {
			eval = mention.aceMention.isSingleton() ? "RIGHT " : (hadAChance ? "WRONG " : "NOCHANCE ");
		} else if (mention.aceMention!=null && ref.aceMention!=null) {
			eval = mention.aceMention.entity == ref.aceMention.entity ? "RIGHT " : (hadAChance ? "WRONG " : "NOCHANCE ");
		} else {
			eval = "";
		}
		if (ref==null) {
			System.out.printf("%sresolved %-15s: M%-2d           %20s\n",
					eval, reason, mention.ID(), ArkParser.abbrevTree(mention.node()));
		} else {
			System.out.printf("%sresolved %-15s: M%-2d -> M%-2d    %20s    ->   %-20s\n",
					eval, reason, mention.ID(), ref.ID(),
					ArkParser.abbrevTree(mention.node()),
					ArkParser.abbrevTree(ref.node()));
		}
	}	
	public static void printGoldAntecedents(Document d, Mention m) {
		for (Mention ant : d.prevMentions(m)) {
			if (ant.aceMention!=null && 
					ant.aceMention.entity == m.aceMention.entity)
				System.out.printf("%s,  ", ant.aceMention);
		}
		System.out.printf("\n");

	}
	public static boolean isThereAGoldAntecedent(Document d, Mention m) {
		if (m.aceMention == null) return true; // weird
		for (Mention ant : d.prevMentions(m)) {
			if (ant.aceMention!=null && 
					ant.aceMention.entity == m.aceMention.entity)
				return true;
		}
		return false;
	}

	/**
	 * 
	 * Note: This is slightly different than what is described in H&K EMNLP 09.
	 * I think the head rules they used were slightly different (or possibly their description is a little off).
	 * 
	 * @param m
	 * @param d
	 * @return
	 */
	private static Mention findAntecedentInRoleAppositiveConstruction(Mention m, Document d) {
		Tree root = m.getSentence().rootNode();
		Tree node = m.node();
		Tree parent = node.parent(root);
		
		//System.err.println("mention:"+node.yield().toString()+"\thead:"+node.headTerminal(QuestionUtil.getInstance().getHeadFinder()).yield().toString());
		if(!parent.label().value().equals("NP")){
			return null;
		}
		
		int index = parent.objectIndexOf(node);
		if(index+1 >= parent.numChildren()){
			return null;
		}
		
		
		TregexPattern pat = TregexPatternFactory.getPattern("NP=parent !> __ <<# (NNP=head ,, NP=mention)");
		TregexMatcher matcher = pat.matcher(parent);
		while (matcher.find()) {
			if (matcher.getNode("mention") == node){
				Tree head = matcher.getNode("head");

				//find maximal projection of the head of the parent
				Tree maxProj = SyntacticPaths.getMaximalProjection(head, root);
				
				//find the mention for the parent
				for(Mention cand:d.mentions()){
					if(cand.node() == maxProj){
						if(Types.personhood(cand) == Types.Personhood.Person){
							return cand;
						}
						break;
					}
				}
			}
		}
		
		return null;
	}

	/**
	 * returns the antecedent NP or null
	 * The way this method is called could be made more efficient.  
	 * It doesn't really need to get called for every mention
	 * 
	 */
	private static Mention findAntecendentInPredicateNominativeConstruction(Mention m, Document d) {
		Tree root = m.getSentence().rootNode();
		Tree node = m.node();
		
		TregexPattern pat = TregexPatternFactory.getPattern("S < NP=np1 <+(VP) (VP < (/^VB.*/ < be|is|was|were|are|being|been) < NP=np2)");
		TregexMatcher matcher = pat.matcher(root);
		while (matcher.find()) {
			if(matcher.getNode("np2") == node){
				Tree ante  = matcher.getNode("np1");
				for(Mention m2: d.mentions()){
					if(ante == m2.node()){
						return m2;
					}
				}
			}
		}
		
		return null;
	}
	
	/**
	 * return true when m is the third child in of a parent who expands as
	 * NP -> NP , NP .*
	 * 
	 * @param m
	 * @return
	 */
	private static boolean inAppositiveConstruction(Mention m) {
		if (m.node()==null) return false;
		Tree root = m.getSentence().rootNode();
		Tree node = m.node();
		Tree parent = node.parent(root);
		
		if(parent.numChildren()<3){
			return false;
		}else if(!parent.getChild(0).label().value().equals("NP")){
			return false;
		}else if(!parent.getChild(1).label().value().equals(",")){
			return false;
		}else if(parent.objectIndexOf(node) != 2){
			return false;
		}
	
		//check to make sure this isn't a conjunction
		for(Tree sibling: parent.getChildrenAsList()){
			if(sibling.label().value().equals("CC")){
				return false;
			}
		}
		
		return true;
	}
	
	public static void resolveAppositive(Mention mention, Document d) {
		Tree root = mention.getSentence().rootNode();
		Tree node = mention.node();
		Tree parent = node.parent(root);
		
		for (Mention cand : d.prevMentions(mention)) {
			if(cand.node() == parent) {
				d.refGraph().setRef(mention, cand);
				reportResolution("appos", mention, cand);
				break;
			}
		}
		
	}
	
	public static void resolvePronoun(Mention mention, Document d) {
		System.out.println("trying to resolve as a pronoun");
		
		ArrayList<Mention> candidates = new ArrayList<Mention>();
	
		for (Mention cand : d.prevMentions(mention)) {
			boolean match = Types.checkPronominalMatch(mention, cand);
			
			if (cand.node() == null) {
				match = false;
			}else if (SyntacticPaths.aIsDominatedByB(mention, cand)){
				 // I-within-I constraint
				//System.out.println("fails A dominates B test");
				match = false;
			} else if (!Types.isReflexive(mention) && SyntacticPaths.inSubjectObjectRelationship(cand, mention)){
				//System.out.println("fails reflexive test");
				match = false;
			} else if (SyntacticPaths.isSubjectAndMentionInAdjunctPhrase(mention, cand)){
				//System.out.println("fails adjunct test");
				match = false;
			}
		
			
			if (match) {
				String s ="";
				if (mention.aceMention!=null & cand.aceMention!=null) {
					boolean gold_match = mention.aceMention.entity==cand.aceMention.entity;
					s = gold_match ? "[gold RIGHT]" : "[gold WRONG]";
				}
//				System.out.printf("PRONOUN CANDIDATE %s: %20s -> %s\n", s, mention, cand);
				if(cand.node() != null){
					candidates.add(cand);
				}
			} else {
//				System.out.println("reject mismatch:  " + cand);
			}
		}
		// HACK HACK
//		if (Types.perspective(mention)==Types.Perspective.Second)
//			candidates.clear();
		if (candidates.size() == 0) {
			System.out.println("No legal candidates");
			d.refGraph().setNullRef(mention);
		} else if (candidates.size() == 1) {
			System.out.println("Single legal resolution");
			d.refGraph().setRef(mention, candidates.get(0));
		} else if (candidates.size() > 1) {
			System.out.println("Finding pronoun antecedent by shortest syntactic path");
			d.refGraph().setRef(mention, SyntacticPaths.findBestCandidateByShortestPath(mention, candidates, d)); 
		}
		Mention ref = d.refGraph().getFinalResolutions().get(mention);
		if(ref != null){
			reportResolution("pronoun", mention,ref);
		}
	}

	public static void resolveOther(Mention mention, Document d, Boolean oracleSemantics) {
		ArrayList<Mention> candidates = new ArrayList<Mention>();
		
		boolean haveSemInfo = false;//Sem.haveNP(mention);
		
		for (Mention cand : d.prevMentions(mention)) {
			Boolean match = null;
			// do while(false): it's GOTO in java.  fun, eh?
			
			DecideCandidate: do {
				if (cand.node() == null) {
					match = false; break DecideCandidate;
				}
				if (Types.isPronominal(cand)) {
					// we only do pronoun-nominal matching in the other direction
					match = false; break DecideCandidate;
				}
				if (SyntacticPaths.aIsDominatedByB(mention, cand)){// I-within-I constraint
					//System.out.println("rejected due to I within I");
					match = false; break DecideCandidate;
				} 
				if (SyntacticPaths.inSubjectObjectRelationship(cand, mention)){
					//System.out.println("rejected due to subj-obj constraint");
					match = false; break DecideCandidate;
				} 
				if (SyntacticPaths.isSubjectAndMentionInAdjunctPhrase(mention, cand)){
					//System.out.println("rejected due to adjunct constraint");
					match = false; break DecideCandidate;
				} 
				if (mention.hasSameHeadWord(cand) || substringMatch(mention, cand)) { 
					match = true; break DecideCandidate;
				}
				if (oracleSemantics) {
					match = mention.aceMention.entity==cand.aceMention.entity;
					System.out.printf("SEMANTICS ORACLE %-5s\t%s\t%s\n",
							match ? "MATCH" : "DIFF",
							Strings.normalizeWhitespace(mention.aceMention.head.charseq.text),
							Strings.normalizeWhitespace(cand.aceMention.head.charseq.text));
					 break DecideCandidate;
					
				}
				if (haveSemInfo && Sem.haveNP(cand)) {
//					if (mention.aceMention.entity==cand.aceMention.entity) {
//						System.out.printf("SEMANTICS PLEASE");
//					} else {
//						System.out.printf("SEMANTICS STOP");
//					}
//					System.out.printf("\t%s\t%s\n", 
//							Strings.normalizeWhitespace(mention.aceMention.head.charseq.text),
//							Strings.normalizeWhitespace(cand.aceMention.head.charseq.text));
					match = Sem.areCompatible(mention, cand);
					break DecideCandidate;
				} 
				
//				System.out.println("Defaulting to reject");
				match = false;
				
			} while(false);
			assert match != null : "if/else logic screwed up!";
			
			if (match) {
//				System.out.println("yay   match:\t" + cand);
				candidates.add(cand);
			} else {
//				System.out.println("reject mismatch:\t" + cand);
			}
		}
		
		if (candidates.size() == 0) {
			System.out.println("No legal candidates");
			d.refGraph().setNullRef(mention);
		} else if (candidates.size() == 1) {
			System.out.println("Single legal resolution");
			d.refGraph().setRef(mention, candidates.get(0));
		} else if (candidates.size() > 1) {
			System.out.println("Finding antecedent by shortest syntactic path");
			d.refGraph().setRef(mention, SyntacticPaths.findBestCandidateByShortestPath(mention, candidates, d)); 
		}
		
		Mention ref = d.refGraph().getFinalResolutions().get(mention);
		if (ref != null){
			reportResolution("other", mention, ref);
		}
		
		//semantics!
	}
	
	private static boolean substringMatch(Mention mention, Mention cand) {
		String mHead = mention.getHeadWord();
		String cHead = cand.getHeadWord();
		String mYield = mention.node().yield().toString();
		String cYield = cand.node().yield().toString();
		
		/*if(mHead.length() >= 5 && cHead.length() >= 5){
			if(mHead.subSequence(0, 5).equals(cHead.subSequence(0, 5))){
				return true;
			}
		}*/
		
		//both must be proper nouns
		if(mention.node().headPreTerminal(ArkParser.getInstance().getHeadFinder()).label().toString().indexOf("NNP") != 0
			 && cand.node().headPreTerminal(ArkParser.getInstance().getHeadFinder()).label().toString().indexOf("NNP") != 0){
				 return false;
		}

		/*if(mHead.indexOf(cHead)!=-1 || cHead.indexOf(mHead)!=-1){
			return true;
		}*/
		
		int matchLen = 4;
		if(mHead.length() >= matchLen && cHead.length() >= matchLen){
			if(mHead.subSequence(0, matchLen).equals(cHead.subSequence(0, matchLen))){// && LevenshteinDistance.getLevenshteinDistance(mHead, cHead) <= 5){
				return true;
			}
		}
		
		/*if(LevenshteinDistance.getLevenshteinDistance(mHead, cHead) < 5){
			return true;
		}*/
		
		return false;
	}
	
}
