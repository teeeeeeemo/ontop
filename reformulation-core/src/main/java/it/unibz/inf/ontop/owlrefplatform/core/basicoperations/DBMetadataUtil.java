package it.unibz.inf.ontop.owlrefplatform.core.basicoperations;

/*
 * #%L
 * ontop-reformulation-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import it.unibz.inf.ontop.model.Function;
import it.unibz.inf.ontop.model.Predicate;
import it.unibz.inf.ontop.model.Term;
import it.unibz.inf.ontop.model.OBDADataFactory;
import it.unibz.inf.ontop.model.impl.OBDADataFactoryImpl;
import it.unibz.inf.ontop.sql.Attribute;
import it.unibz.inf.ontop.sql.DBMetadata;
import it.unibz.inf.ontop.sql.ForeignKeyConstraint;
import it.unibz.inf.ontop.sql.Relation2DatalogPredicate;
import it.unibz.inf.ontop.sql.DatabaseRelationDefinition;
import it.unibz.inf.ontop.sql.UniqueConstraint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;


public class DBMetadataUtil {

	private static OBDADataFactory fac = OBDADataFactoryImpl.getInstance();
	
	
	/***
	 * Generates a map for each predicate in the body of the rules in 'program'
	 * that contains the Primary Key data for the predicates obtained from the
	 * info in the metadata.
     *
     * It also returns the columns with unique constraints
     *
     * For instance, Given the table definition
     *   Tab0[col1:pk, col2:pk, col3, col4:unique, col5:unique],
     *
     * The methods will return the following Multimap:
     *  { Tab0 -> { [col1, col2], [col4], [col5] } }
     *
	 * 
	 * @param metadata
	 * @param program
	 */
	public static Multimap<Predicate, List<Integer>> extractPKs(DBMetadata metadata) {
		
		Multimap<Predicate, List<Integer>> pkeys = HashMultimap.create();
		for (DatabaseRelationDefinition relation : metadata.getDatabaseRelations()) {
			Predicate predicate = Relation2DatalogPredicate.createPredicateFromRelation(relation);
			
			// primary key and unique constraints
			for (UniqueConstraint uc : relation.getUniqueConstraints()) {
				List<Integer> pkeyIdx = new ArrayList<>(uc.getAttributes().size());
				for (Attribute att : uc.getAttributes()) 
					pkeyIdx.add(att.getIndex());
				
				pkeys.put(predicate, pkeyIdx);
			}
		}
		return pkeys;
	}
	
	
	
	/*
	 * generate CQIE rules from foreign key info of db metadata
	 * TABLE1.COL1 references TABLE2.COL2 as foreign key then 
	 * construct CQIE rule TABLE2(P1, P3, COL2, P4) :- TABLE1(COL2, T2, T3).
	 */
	public static LinearInclusionDependencies generateFKRules(DBMetadata metadata) {
		LinearInclusionDependencies dependencies = new LinearInclusionDependencies();
		final boolean printouts = false;
		
		if (printouts)
			System.out.println("===FOREIGN KEY RULES");
		int count = 0;
		Collection<DatabaseRelationDefinition> tableDefs = metadata.getDatabaseRelations();
		for (DatabaseRelationDefinition def : tableDefs) {
			for (ForeignKeyConstraint fks : def.getForeignKeys()) {

				DatabaseRelationDefinition def2 = (DatabaseRelationDefinition) fks.getReferencedRelation();

				Map<Integer, Integer> positionMatch = new HashMap<>();
				for (ForeignKeyConstraint.Component comp : fks.getComponents()) {
					// Get current table and column (1)
					Attribute att1 = comp.getAttribute();
					
					// Get referenced table and column (2)
					Attribute att2 = comp.getReference();				
					
					// Get positions of referenced attribute
					int pos1 = att1.getIndex();
					int pos2 = att2.getIndex();
					positionMatch.put(pos1 - 1, pos2 - 1); // indexes start at 1
				}
				// Construct CQIE
				int len1 = def.getAttributes().size();
				List<Term> terms1 = new ArrayList<>(len1);
				for (int i = 1; i <= len1; i++) 
					 terms1.add(fac.getVariable("t" + i));
				
				// Roman: important correction because table2 may not be in the same case 
				// (e.g., it may be all upper-case)
				int len2 = def2.getAttributes().size();
				List<Term> terms2 = new ArrayList<>(len2);
				for (int i = 1; i <= len2; i++) 
					 terms2.add(fac.getVariable("p" + i));
				
				// do the swapping
				for (Entry<Integer,Integer> swap : positionMatch.entrySet()) 
					terms1.set(swap.getKey(), terms2.get(swap.getValue()));
				
				Function head = Relation2DatalogPredicate.getAtom(def2, terms2);
				Function body = Relation2DatalogPredicate.getAtom(def, terms1);
				
				dependencies.addRule(head, body);				
				if (printouts)
					System.out.println("   FK_" + ++count + " " +  head + " :- " + body);
			}
		}		
		if (printouts)
			System.out.println("===END OF FOREIGN KEY RULES");
		return dependencies;
	}
}
