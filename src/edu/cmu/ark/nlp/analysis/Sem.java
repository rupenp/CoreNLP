package edu.cmu.ark.nlp.analysis;


import edu.cmu.ark.nlp.data.CDB;
import edu.cmu.ark.nlp.data.Mention;
import edu.cmu.ark.nlp.data.CDB.FV;


public class Sem {

	public static boolean haveNP(Mention mention) {
		return CDB.I().haveNP(getNPString(mention));
	}
	
	public static String getNPString(Mention mention) {
		if (mention.aceMention != null) {
			return mention.aceMention.head.charseq.text;
		}
		return mention.getHeadWord(); // egads, usually too small.
	}

	/** this is really crappy **/
	public static boolean areCompatible(Mention mention, Mention cand) {
		
		String np1 = getNPString(mention);
		String np2 = getNPString(cand);
		
		FV fv1 = CDB.I().getContextVector(np1);
		FV fv2 = CDB.I().getContextVector(np2);
		
		return FV.cos(fv1,fv2) > 0.30;
		
//		double score = 0;
//		for (String key : FV.keyIntersect(fv1, fv2)) {
//			double cc = CDB.I().contextCounts.get(key);
//			System.out.printf("CONTEXT ");
//			System.out.printf("match=%s ", mention.aceMention.entity==cand.aceMention.entity ? "TRUE" : "FALSE");
//			System.out.printf("np1=%s np2=%s ", np1.replace(" ","_"), np2.replace(" ","_"));
//			System.out.printf("context_count=%s ", cc);
//			System.out.printf("context=%s ", key.replace(" ","_"));
//			System.out.printf("np1count=%s np2count=%s ", CDB.I().npCounts.get(np1), CDB.I().npCounts.get(np2));
//			System.out.printf("np1cc=%s np2cc=%s ", fv1.map.get(key), fv2.map.get(key) );
//			System.out.printf("\n");
//		}
		
//		System.out.printf("REPORT  %10s (%.1e)  -- vs --  %10s (%.1e)\n", 
//				np1, 1.0*CDB.I().npCounts.get(np1),
//				np2, 1.0*CDB.I().npCounts.get(np2));
//		FV.pairReport(fv1, fv2);
		
//		System.out.printf("CONTEXT ");
//		System.out.printf("match=%s ", mention.aceMention.entity==cand.aceMention.entity ? "TRUE" : "FALSE");
//		System.out.printf("np1=%s np2=%s ", np1.replace(" ","_"), np2.replace(" ","_"));
//		System.out.printf("rho=%.3f ", FV.spearman(fv1, fv2) );
		
//		return false;
		
//		Set<String> cs1 = CDB.I.getContexts(np1);
//		Set<String> cs2 = CDB.I.getContexts(np2);
//		double jacc = CDB.jaccard(cs1, cs2);
//		System.out.printf("JACC %.3f    %-10s --- %s\n", jacc, np1, np2);
//		return jacc > 0.5;
	}

}
