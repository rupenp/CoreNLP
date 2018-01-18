package edu.cmu.ark.nlp.analysis;

import edu.cmu.ark.nlp.data.Document;
import edu.cmu.ark.nlp.data.EntityGraph;
import edu.cmu.ark.nlp.data.Mention;

/** Do the transitive closure to make reference-referent pairs into entity partitions **/
public class RefsToEntities {
	public static void go(Document d) {
		EntityGraph eg = new EntityGraph(d);
		for (Mention m1 : d.refGraph().getFinalResolutions().keySet()) {
			if (d.refGraph().getFinalResolutions().get(m1) != null) {
				eg.addPair(m1, d.refGraph().getFinalResolutions().get(m1));
			}
		}
		eg.freezeEntities();
		
		d.setEntGraph(eg);
		
		System.out.println("\n*** Entity Report ***\n");
		int s=-1;
		for (Mention m : d.mentions()){
			if (m.getSentence().ID() != s) {
				s = m.getSentence().ID();
				System.out.printf("S%-2s  %s\n",s, m.getSentence().text());
			}
			System.out.printf("  ");
			if (m.aceMention != null) {
				System.out.printf("%-3s ", m.aceMention.isSingleton() ? "" : m.aceMention.entity);
			}
			if (eg.isSingleton(m)) {
				System.out.printf("%-20s  %s\n", "singleton", m);
			} else {
				System.out.printf("%-20s  %s\n", "entity_"+eg.entName(m), m);
			}
		}
//		System.out.println("");
	}
}
