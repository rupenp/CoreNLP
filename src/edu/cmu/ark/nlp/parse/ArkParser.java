package edu.cmu.ark.nlp.parse;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.StringUtils;

import com.aliasi.util.Strings;


import edu.cmu.ark.nlp.tagger.supersense.DiscriminativeTagger;
import edu.cmu.ark.nlp.tagger.supersense.LabeledSentence;
import edu.cmu.ark.nlp.sent.SentenceBreaker;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;

//import net.didion.jwnl.*;

/** Various NL analysis utilities, including ones wrapping Stanford subsystems and other misc stuff **/
public class ArkParser {
	public static boolean DEBUG = true;
	private Map<String, Map<String, String>> morphMap; //pos, word -> stem
	private DiscriminativeTagger sst;
	private LexicalizedParser parser;
	private static ArkParser instance;
	private CollinsHeadFinder headfinder;
	private LabeledScoredTreeFactory tree_factory;
	private PennTreebankLanguagePack tlp;
	private double lastParseScore;
	private Tree lastParse;
	public DocumentPreprocessor dp;
	
	private String serializedParserFile;
	private String parserMaxLen;
	private String parserOutputFormat;
	
	private static final String DEFAULT_PARSER_GRAMMAR_FILE = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
	private static final String DEFAULT_PARSER_MAX_LEN = "50";
	private static final String DEFAULT_PARSER_OUTPUT_FORMAT = "oneline";
	
	 
	
	public ArkParser(Properties props){
		parser = null;
		sst = null;
		dp = null;
		if (props != null) {
			this.serializedParserFile = props.getProperty("parserGrammarFile", DEFAULT_PARSER_GRAMMAR_FILE);
			this.parserMaxLen = props.getProperty("parserMaxLen", DEFAULT_PARSER_MAX_LEN);
			this.parserOutputFormat = props.getProperty("parserOutputFormat", DEFAULT_PARSER_OUTPUT_FORMAT);
		}else {
			this.serializedParserFile = DEFAULT_PARSER_GRAMMAR_FILE;
			this.parserMaxLen = DEFAULT_PARSER_MAX_LEN;
			this.parserOutputFormat = DEFAULT_PARSER_OUTPUT_FORMAT;
		}
		
		parser = LexicalizedParser.getParserFromSerializedFile(this.serializedParserFile);				
		parser.setOptionFlags("-maxLength", this.parserMaxLen);
		parser.setOptionFlags("-outputFormat", this.parserOutputFormat);
			
		headfinder = new CollinsHeadFinder();
		tree_factory = new LabeledScoredTreeFactory();
		tlp = new PennTreebankLanguagePack();
	}
	
	protected static String preprocess(String sentence) {
		sentence = sentence.trim();
		if(!sentence.matches(".*\\.['\"]*$")){//charAt(sentence.length()-1) != '.'){
			sentence += ".";
		}
		
		sentence = sentence.replaceAll("can't", "can not");
		sentence = sentence.replaceAll("won't", "will not");
		sentence = sentence.replaceAll("n't", " not"); //aren't shouldn't don't isn't
		
		return sentence;
	}
		
	protected static String preprocessTreeString(String sentence) {
		sentence = sentence.replaceAll(" n't", " not");
		sentence = sentence.replaceAll("\\(MD ca\\)", "(MD can)");
		sentence = sentence.replaceAll("\\(MD wo\\)", "(MD will)");
		sentence = sentence.replaceAll("\\(MD 'd\\)", "(MD would)");
		sentence = sentence.replaceAll("\\(VBD 'd\\)", "(VBD had)");
		sentence = sentence.replaceAll("\\(VBZ 's\\)", "(VBZ is)");
		sentence = sentence.replaceAll("\\(VBZ 's\\)", "(VBZ is)");
		sentence = sentence.replaceAll("\\(VBZ 's\\)", "(VBZ is)");
		sentence = sentence.replaceAll("\\(VBP 're\\)", "(VBP are)");
		
		return sentence;
	}
	
	public static int[] alignTokens(String rawText, List<edu.cmu.ark.nlp.data.Word> words) {
		String[] tokens = new String[words.size()];
		for (int i=0; i < words.size(); i++) {
			tokens[i] = words.get(i).token;
		}
		return alignTokens(rawText, tokens);
	}

	public static int[] alignTokens(String rawText, String[] tokens) {
		int MAX_ALIGNMENT_SKIP = 100;
		int[] alignments = new int[tokens.length];
		int curPos = 0;
		
		tok_loop:
		
		for (int i=0; i < tokens.length; i++) {
			String tok = tokens[i];
//			System.out.printf("TOKEN [%s]  :  ", tok);
			for (int j=0; j < MAX_ALIGNMENT_SKIP; j++) {
				boolean directMatch  = rawText.regionMatches(curPos + j, tok, 0, tok.length());
				if (!directMatch)
					directMatch = rawText.toLowerCase().regionMatches(curPos + j, tok.toLowerCase(), 0, tok.length());
				boolean alternateMatch = false;
				if (!directMatch) {
					int roughLast = curPos+j+tok.length()*2+10;
					String substr = StringUtils.substring(rawText, curPos+j, roughLast);
					Matcher m = tokenSurfaceMatches(tok).matcher(substr);
//					System.out.println("PATTERN "+ tokenSurfaceMatches(tok));
					alternateMatch = m.find() && m.start()==0;
				}
				
//				System.out.println("MATCHES "+ directMatch + " " + alternateMatch);
				if (directMatch || alternateMatch) {
					alignments[i] = curPos+j;
					if (directMatch)
						curPos = curPos+j+tok.length();
					else
						curPos = curPos+j+1;
//					System.out.printf("\n  Aligned to pos=%d : [%s]\n", alignments[i], U.backslashEscape(StringUtils.substring(rawText, alignments[i], alignments[i]+10)));
					continue tok_loop;
				}
//				System.out.printf("%s", U.backslashEscape(StringUtils.substring(rawText,curPos+j,curPos+j+1)));
			}
			System.out.printf("FAILED MATCH for token [%s]\n", tok);
			System.out.println("sentence: "+rawText);
			System.out.println("tokens: " + StringUtils.join(tokens," "));
			alignments[i] = -1;
		}
		// TODO backoff for gaps .. at least guess the 2nd gap position or something (2nd char after previous token ends...)
		return alignments;
	}
	
	/** undo penn-treebankification of tokens.  want to match raw original form if possible. **/
	public static Pattern tokenSurfaceMatches(String tok) {
		if (tok.equals("-LRB-")) {
			return Pattern.compile("[(\\[]");
		} else if (tok.equals("-RRB-")) {
			return Pattern.compile("[)\\]]");
		} else if (tok.equals("``")) {
			return Pattern.compile("(\"|``)");
		} else if (tok.equals("''")) {
			return Pattern.compile("(\"|'')");
		} else if (tok.equals("`")) {
			return Pattern.compile("('|`)");
		}
		return Pattern.compile(Pattern.quote(tok));
	}
	
	public List<String> stanfordTokenize(String str) {
		DocumentPreprocessor docPreprocessor = new DocumentPreprocessor(str);
		List<String> tokens = new ArrayList<String>();
		
		for (List<HasWord> sentence : docPreprocessor) {
			for (HasWord word : sentence) {
				tokens.add(word.word().toString());
	        }
	    }
		return tokens;
	}
	
	public static List <SentenceBreaker.Sentence> cleanAndBreakSentences(String docText) {
		// ACE IS EVIL
		docText = docText.replaceAll("<\\S+>", "");
		AlignedSub cleaner = ArkParser.cleanupDocument(docText);
		List<SentenceBreaker.Sentence> sentences = SentenceBreaker.getSentences(cleaner);
		return sentences;
	}

	public static List <String> cleanAndBreakSentencesToText(String docText) {
		List <String> sentenceTexts = new ArrayList<String>();
		for (SentenceBreaker.Sentence s : cleanAndBreakSentences(docText))
			sentenceTexts.add( s.cleanText );
		return sentenceTexts;
	}
	
	/** uses stanford library for document cleaning and sentence breaking **/
	public List<String> getSentencesStanford(String document) {
		List<String> res = new ArrayList<String>();
		StringReader reader = new StringReader(cleanupDocument(document).text);
		
        DocumentPreprocessor docPreprocessor = new DocumentPreprocessor(reader);
		
		for (List<HasWord> sentence : docPreprocessor) {
	        res.add(SentenceUtils.listToString(sentence, true));
	    }
		
		return res;
	}
	
	static Pattern leadingWhitespace = Pattern.compile("^\\s+");
	/** some ACE docs have weird markup in them that serve as paragraph-ish markers **/
	public static AlignedSub cleanupDocument(String document) {
		AlignedSub ret = new AlignedSub(document);
		ret = ret.replaceAll("<\\S+>", "");
		ret = ret.replaceAll(leadingWhitespace, ""); // sentence breaker char offset correctness sensitive to this
		return ret;
	}

	public static AlignedSub moreCleanup(String str) {
		AlignedSub ret = new AlignedSub(str);
		ret = ret.replaceAll("&(amp|AMP);", "&");
		ret = ret.replaceAll("&(lt|LT);", "<");
		ret = ret.replaceAll("&(gt|GT);", ">");
		return ret;
	}
		
	public CollinsHeadFinder getHeadFinder(){
		return headfinder;
	}
	
	public static ArkParser getInstance(Properties props){
		if(instance == null){
			instance = new ArkParser(props);
		}
		return instance;
	}
	
	public static ArkParser getInstance(){
		if(instance == null){
			instance = new ArkParser(null);
		}
		return instance;
	}
	
	public double getLastParseScore(){
		return lastParseScore;
	}
	
	public Double getLastParseScoreNormalizedByLength() {
		double length = lastParse.yield().size();
		double res = lastParseScore;
		if(length <= 0){
			res = 0.0;
		}else{
			res /= length;
		}
		return res;
	}
	
	public static class ParseResult {
		public boolean success;
		public Tree parse;
		public double score;
		public ParseResult(boolean s, Tree p, double sc) { success=s; parse=p; score=sc; }
	}
	
	public ParseResult parseSentence(String sentence) throws IOException {
		try{
			Tree lastParse = parser.parse(sentence);
			lastParseScore = lastParse.score();
			StringWriter sb = new StringWriter();
			PrintWriter pw = new PrintWriter(sb);
			lastParse.pennPrint(pw);
			pw.flush();
			return new ParseResult(true, lastParse, lastParseScore);
		}catch(Exception e){
			e.printStackTrace();
		}

		lastParse = readTreeFromString("(ROOT (. .))");
        lastParseScore = -99999.0;
        return new ParseResult(false, lastParse, lastParseScore);
	}
	
	public List<String> annotateSentenceWithSupersenses(Tree sentence) {
		List<String> result = new ArrayList<String>();
		
		int numleaves = sentence.getLeaves().size();
		if(numleaves <= 1){
			return result;
		}
		LabeledSentence labeled = generateSupersenseTaggingInput(sentence);
		if(result.size() == 0){
			try {
				if(sst == null){
					sst = new DiscriminativeTagger();
				}
				sst.findBestLabelSequenceViterbi(labeled, sst.getWeights());
				for(String pred: labeled.getPredictions()){
					result.add(pred);
				}
			} catch (Exception e){
				e.printStackTrace();
			}
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
			String stem = ArkParser.getInstance().getLemma(word, pos);
			res.addToken(word, stem, pos, "0");
		}
		
		return res;
	}

	/**
	 * Remove traces and non-terminal decorations (e.g., "-SUBJ" in "NP-SUBJ") from a Penn Treebank-style tree.
	 * 
	 * @param inputTree
	 */
	public void normalizeTree(Tree inputTree){
		inputTree.label().setFromString("ROOT");

		List<Pair<TregexPattern, TsurgeonPattern>> ops = new ArrayList<Pair<TregexPattern, TsurgeonPattern>>();
		List<TsurgeonPattern> ps = new ArrayList<TsurgeonPattern>();
		String tregexOpStr;
		TregexPattern matchPattern;
		TsurgeonPattern p;
		TregexMatcher matcher;
		
		tregexOpStr = "/\\-NONE\\-/=emptynode";
		matchPattern = TregexPatternFactory.getPattern(tregexOpStr);
		matcher = matchPattern.matcher(inputTree);
		ps.add(Tsurgeon.parseOperation("prune emptynode"));
		matchPattern = TregexPatternFactory.getPattern(tregexOpStr);
		p = Tsurgeon.collectOperations(ps);
		ops.add(new Pair<TregexPattern,TsurgeonPattern>(matchPattern,p));
		Tsurgeon.processPatternsOnTree(ops, inputTree);
		
		Label nonterminalLabel;
		
		tregexOpStr = "/.+\\-.+/=nonterminal < __";
		matchPattern = TregexPatternFactory.getPattern(tregexOpStr);
		matcher = matchPattern.matcher(inputTree);
		while(matcher.find()){
			nonterminalLabel = matcher.getNode("nonterminal");
			if(nonterminalLabel == null) continue;
			nonterminalLabel.setFromString(tlp.basicCategory(nonterminalLabel.value()));
		}
		

	}
		
	public static String getCleanedUpYield(Tree inputTree){
		Tree copyTree = inputTree.deepCopy();

		if(DEBUG)System.err.println(copyTree.toString());

		String res = copyTree.yield().toString();
		if(res.length() > 1){
			res = res.substring(0,1).toUpperCase() + res.substring(1);
		}

		//(ROOT (S (NP (NNP Jaguar) (NNS shares)) (VP (VBD skyrocketed) (NP (NN yesterday)) (PP (IN after) (NP (NP (NNP Mr.) (NNP Ridley) (POS 's)) (NN announcement)))) (. .)))
		
		res = res.replaceAll("\\s([\\.,!\\?\\-;:])", "$1");
		res = res.replaceAll("(\\$)\\s", "$1");
		res = res.replaceAll("can not", "cannot");
		res = res.replaceAll("\\s*-LRB-\\s*", " (");
		res = res.replaceAll("\\s*-RRB-\\s*", ") ");
		res = res.replaceAll("\\s*([\\.,?!])\\s*", "$1 ");
		res = res.replaceAll("\\s+''", "''");
		//res = res.replaceAll("\"", "");
		res = res.replaceAll("``\\s+", "``");
		res = res.replaceAll("\\-[LR]CB\\-", ""); //brackets, e.g., [sic]

		//remove extra spaces
		res = res.replaceAll("\\s\\s+", " ");
		res = res.trim();

		return res;
	}
		
	public Tree readTreeFromString(String parseStr) throws IOException{
		//read in the input into a Tree data structure
		TreeReader treeReader = new PennTreeReader(new StringReader(parseStr), tree_factory);
		Tree inputTree = null;
		try{
			inputTree = treeReader.readTree();
			
		}catch(IOException e){
			e.printStackTrace();
		}
		finally{
			treeReader.close();
		}
		return inputTree;
	}
	
	protected static boolean filterSentenceByPunctuation(String sentence) {
		//return (sentence.indexOf("\"") != -1 
				//|| sentence.indexOf("''") != -1 
				//|| sentence.indexOf("``") != -1
				//|| sentence.indexOf("*") != -1);
				return (sentence.indexOf("*") != -1);
	}
		
	/**
	 * Sets the parse and score.
	 * For use when the input tree is given (e.g., for gold standard trees from a treebank)
	 * 
	 * @param parse
	 * @param score
	 */
	public void setLastParseAndScore(Tree parse, double score){
		lastParse = parse;
		lastParseScore = score;
	}
	
	/** 
	 * terse representation of a (sub-)tree: 
	 * NP[the white dog]   -vs-   (NP (DT the) (JJ white) (NN dog)) 
	 **/
	public static String abbrevTree(Tree tree) {
		ArrayList<String> toks = new ArrayList();
		for (Tree L : tree.getLeaves()) {
			toks.add(L.label().toString());
		}
		return tree.label().toString() + "[" + StringUtils.join(toks, " ") + "]";
	}
	
	private void loadWordnetMorphologyCache(String morphFile) {
		morphMap = new HashMap<String, Map<String, String>>();
		
		try{
			BufferedReader br;
			String buf;
			String[] parts;
			
			br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(morphFile))));
			while((buf = br.readLine())!= null){
				parts = buf.split("\\t");
				addMorph(parts[1], parts[0], parts[2]);
				addMorph(parts[1], "UNKNOWN", parts[2]);
			}
			br.close();
			addMorph("men", "NNS", "man");
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	private void addMorph(String word, String pos, String stem){
		Map<String, String> posMap = morphMap.get(pos);
		if(posMap == null){
			posMap = new HashMap<String, String>();
			morphMap.put(pos.intern(), posMap);
		}
		
		posMap.put(word.intern(), stem.intern());
	}
		
	public String getLemma(String word, String pos){
		/*
		if(morphMap == null){
			loadWordnetMorphologyCache();
		}
		*/
		String res = word;
		Map<String, String> posMap = morphMap.get(pos);
		if(posMap != null){
			res = posMap.get(word.toLowerCase());
			if(res == null){
				res = word.toLowerCase();
			}
		}
		return res;
	}

}
