package edu.cmu.ark.nlp.data;

import static edu.stanford.nlp.util.logging.Redwood.Util.log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.StringUtils;

/**
 * For identifying the genders of given names.
 * Uses US Census data from 
 * 
 *	http://www.census.gov/genealogy/names/dist.female.first
 *	http://www.census.gov/genealogy/names/dist.male.first
 * 
 * @author Michael Heilman
 *
 */
public class FirstNames {
	@ArgumentParser.Option(name="maleNamesPath", gloss="file path for the male first names")
	protected String maleNamesPath = null; // currently not used
	@ArgumentParser.Option(name="femaleNamesPath", gloss="file path for the female first names")
	protected String femaleNamesPath = null;

	private Map<String, NameGender> genderMap;
	private static final String DEFAULT_MALE_FIRSTNAMES_FILEPATH = "edu/cmu/ark/nlp/supersense/wordlists/dist.male.first.80percent";
	private static final String DEFAULT_FEMALE_FIRSTNAMES_FILEPATH = "edu/cmu/ark/nlp/supersense/wordlists/dist.female.first.80percent";

	private static FirstNames instance;

	//I did not use the same gender enumeration as in 
	//the types class to avoid having that extra 
	//dependency.  I want to be able to take this
	//class and use it for other stuff if I want. --MJH
	private static enum NameGender {
		Male, Female, Unknown;
		public String toString() {
			switch(this) {
			case Male: return "Mal";
			case Female: return "Fem";
			case Unknown: return "Unk";
			}
			return "Unk";
		}
	}
	
	public FirstNames() {
		this(null);
	}

	public FirstNames(Properties props){
		genderMap = new HashMap<String, NameGender>();
		if (this.maleNamesPath == null) {
			if (props != null) {
				this.maleNamesPath = props.getProperty("maleNamesPath", DEFAULT_MALE_FIRSTNAMES_FILEPATH);
			}else {
				this.maleNamesPath = DEFAULT_MALE_FIRSTNAMES_FILEPATH;
			}
		}
		if (this.femaleNamesPath == null) {
			if (props != null) {
				this.femaleNamesPath = props.getProperty("femaleNamesPath", DEFAULT_FEMALE_FIRSTNAMES_FILEPATH);
			}else {
				this.femaleNamesPath = DEFAULT_FEMALE_FIRSTNAMES_FILEPATH;
			}
		}

		//load U.S. census data 
		log("loading first names");
		//Temporarily keep frequencies of male names to
		//make decisions about ambiguous names.
		Map<String, Double> maleFrequencies = loadNameFrequencies(this.maleNamesPath);
		Map<String, Double> femaleFrequencies = loadNameFrequencies(this.femaleNamesPath);

		//add male names
		for(Map.Entry<String, Double> entry: maleFrequencies.entrySet()){
			genderMap.put(entry.getKey(), NameGender.Male);
		}

		//add female names, check frequencies for ambiguous names
		String name;
		Double freq;
		for(Map.Entry<String, Double> entry: femaleFrequencies.entrySet()){
			name = entry.getKey();
			freq = entry.getValue();

			if(maleFrequencies.get(name) == null || maleFrequencies.get(name) < freq){
				genderMap.put(name, NameGender.Female);
			}
		}

	}

	private Map<String, Double> loadNameFrequencies(String path){
		Map<String, Double> res = new HashMap<String, Double>();

		String buf;
		String [] parts;
		String name;
		Double freq;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
			while((buf=br.readLine()) != null){
				parts = buf.split("\\s+");
				name = parts[0].toLowerCase();
				freq = new Double(parts[1]);

				res.put(name, freq);
			}


		} catch (IOException e) {
			e.printStackTrace();
		}

		return res;
	}


	public static FirstNames getInstance(Properties props) {
		if(instance == null){
			instance = new FirstNames(props);
		}
		return instance;
	}
	
	public static FirstNames getInstance() {
		if(instance == null){
			instance = new FirstNames(null);
		}
		return instance;
	}


	public Set<String> getMaleNames(){
		Set<String> res = new HashSet<String>();

		for(Map.Entry<String, NameGender> entry: genderMap.entrySet()){
			if(entry.getValue() == NameGender.Male){
				res.add(entry.getKey());
			}
		}

		return res;
	}

	public Set<String> getFemaleNames(){
		Set<String> res = new HashSet<String>();

		for(Map.Entry<String, NameGender> entry: genderMap.entrySet()){
			if(entry.getValue() == NameGender.Female){
				res.add(entry.getKey());
			}
		}

		return res;
	}

	public Set<String> getAllFirstNames(){
		Set<String> res = new HashSet<String>();

		for(Map.Entry<String, NameGender> entry: genderMap.entrySet()){
			res.add(entry.getKey());
		}

		return res;
	}

	
	public String getGenderString(String name, Properties props) {
		String genderS;
		NameGender gender = FirstNames.getInstance(props).getGender(name);
		if(gender == NameGender.Male){
			genderS = "Mal";
		}else if (gender == NameGender.Female){
			genderS = "Fem";
		}else{
			genderS = "";
		}
		return genderS;
	}
	
	public String getGenderString(String name) {
		String genderS;
		NameGender gender = FirstNames.getInstance(null).getGender(name);
		if(gender == NameGender.Male){
			genderS = "Mal";
		}else if (gender == NameGender.Female){
			genderS = "Fem";
		}else{
			genderS = "";
		}
		return genderS;
	}

	public NameGender getGender(String name) {
		NameGender res;
		NameGender gender = genderMap.get(name.toLowerCase());
		if(gender == null){
			res = NameGender.Unknown;
		}else{
			res = gender;
		}

		return res;
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		log("--- " + FirstNames.class.getSimpleName() + "#main() called ---");
		// Fill arguments
		ArgumentParser.fillOptions(FirstNames.class, args);
		// get properties from command line
		Properties props = StringUtils.argsToProperties(args);

		String buf;
		String genderS;

		//pre-load
		FirstNames.getInstance(props);

		System.err.println("Type names on standard input...");

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while((buf = br.readLine()) != null){
			buf = buf.trim();
			genderS = FirstNames.getInstance(props).getGenderString(buf, props);

			log(genderS);
		}

	}
}
