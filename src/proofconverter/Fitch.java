package proofconverter;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class Fitch {
	
	private static String[] subProofRules = {"NEGATION_INTRODUCTION", "DISJUNCTION_ELIMINATION", "CONDITIONAL_INTRODUCTION", "BICONDITIONAL_INTRODUCTION"};
	private static Map<Integer, Proof> proofs;
	private static List<Step> steps = new ArrayList<Step>();
	
	public static String parse(NodeList proofList) {
		proofs = new HashMap<Integer, Proof>();
		
		for(int i = 0; i < proofList.getLength(); i++) {
			Node proofNode = proofList.item(i);
			
			if(proofNode.getNodeType() == Node.ELEMENT_NODE) {
				Proof p = new Proof((Element) proofNode, "Fitch");
				proofs.put(p.getID(), p);
			}
		}
		
		setIndents(proofs.get(1), 0);
		Collections.sort(steps, new StepComparer());
		
		String output = "";
		for(int i = 0; i < steps.size(); i++) {
			String lineNum = steps.get(i).getLineNum() + ". ";
			String indent = "";
			for(int j = 0; j < steps.get(i).getIndent(); j++) {
				indent += "| ";
			}
			String sentence = steps.get(i).getSentence().printSentence();
			String rule = steps.get(i).getRule();
			String premises = "";
			Boolean subProofPremise = false;
			for(int j = 0; j < subProofRules.length; j++) {
				if(rule.equals(subProofRules[j])) {
					subProofPremise = true;
					break;
				} 
			}
			if(!subProofPremise) {
				for(int k = 0; k < steps.get(i).getNumPremises(); k++) {
					premises += " " + steps.get(i).getPremise(k);
				}
			} else {
				if(steps.get(i).getRule().equals("DISJUNCTION_ELIMINATION")) {
					premises += " " + steps.get(i).getPremise(0);
					premises += " " + proofs.get(Integer.valueOf(steps.get(i).getPremise(1))).getStartLine() + "-" + proofs.get(Integer.valueOf(steps.get(i).getPremise(1))).getEndLine();
					premises += " " + proofs.get(Integer.valueOf(steps.get(i).getPremise(2))).getStartLine() + "-" + proofs.get(Integer.valueOf(steps.get(i).getPremise(2))).getEndLine();
				} else {
					for(int k = 0; k < steps.get(i).getNumPremises(); k++) {
						premises += " " + proofs.get(Integer.valueOf(steps.get(i).getPremise(k))).getStartLine() + "-" + proofs.get(Integer.valueOf(steps.get(i).getPremise(k))).getEndLine();
					}
				}
			}
			output += String.format("%-6s%-32s%-30s\r\n", lineNum, indent + sentence, rule + premises);
		}
		
		return output;
	}
	
	public static Document convert(NodeList proofList, Document outputDoc) {
		proofs = new HashMap<Integer, Proof>();
		
		for(int i = 0; i < proofList.getLength(); i++) {
			Node proofNode = proofList.item(i);
			
			if(proofNode.getNodeType() == Node.ELEMENT_NODE) {
				Proof p = new Proof((Element) proofNode, "Fitch");
				proofs.put(p.getID(), p);
			}
		}
		
		Proof newProof = convertProof();
		
		Element rootElement = outputDoc.createElement("bram");
		
		Element program = outputDoc.createElement("Program");
		program.appendChild(outputDoc.createTextNode("Sequent"));
		
		Element version = outputDoc.createElement("Version");
		version.appendChild(outputDoc.createTextNode("1.0"));
		
		Element metadata = outputDoc.createElement("metadata");
		metadata.appendChild(outputDoc.createTextNode(" "));
		
		Element proof = outputDoc.createElement("proof");
		proof.setAttribute("id", "1");
		
		Element goal = outputDoc.createElement("goal");
		Element goalSen = outputDoc.createElement("sen");
		goalSen.appendChild(outputDoc.createTextNode(newProof.getGoal().printSentencePrefix()));
		goal.appendChild(goalSen);
		proof.appendChild(goal);
		
		for(int i = 0; i < newProof.getNumSteps(); i++) {
			Step s = newProof.getStep(i);
			if(s.getRule().equals("ASSUMPTION")) {
				Element assumption = outputDoc.createElement("assumption");
				assumption.setAttribute("linenum", Integer.toString(s.getLineNum()));
				
				Element sequent = outputDoc.createElement("sequent");
				List<Sentence> seq = ((SequentStep) s).getSequent();
				
				for(int j = 0; j < seq.size(); j++) {
					Element ant = outputDoc.createElement("ant");
					ant.appendChild(outputDoc.createTextNode(seq.get(j).printSentencePrefix()));
					sequent.appendChild(ant);
				}
				
				Element sen = outputDoc.createElement("sen");
				sen.appendChild(outputDoc.createTextNode(s.getSentence().printSentencePrefix()));
				
				assumption.appendChild(sequent);
				assumption.appendChild(sen);
				
				proof.appendChild(assumption);
			} else {
				Element step = outputDoc.createElement("step");
				step.setAttribute("linenum", Integer.toString(s.getLineNum()));
				
				Element sequent = outputDoc.createElement("sequent");
				List<Sentence> seq = ((SequentStep) s).getSequent();
				
				for(int j = 0; j < seq.size(); j++) {
					Element ant = outputDoc.createElement("ant");
					ant.appendChild(outputDoc.createTextNode(seq.get(j).printSentencePrefix()));
					sequent.appendChild(ant);
				}
				
				Element sen = outputDoc.createElement("sen");
				sen.appendChild(outputDoc.createTextNode(s.getSentence().printSentencePrefix()));
				
				step.appendChild(sequent);
				step.appendChild(sen);
				
				for(int j = 0; j < s.getNumPremises(); j++) {
					Element premise = outputDoc.createElement("premise");
					premise.appendChild(outputDoc.createTextNode(s.getPremise(j)));
					step.appendChild(premise);
				}
				
				Element rule = outputDoc.createElement("rule");
				rule.appendChild(outputDoc.createTextNode(s.getRule()));
				
				step.appendChild(rule);
				
				proof.appendChild(step);
			}
		}
		
		rootElement.appendChild(program);
		rootElement.appendChild(version);
		rootElement.appendChild(metadata);
		rootElement.appendChild(proof);
		
		outputDoc.appendChild(rootElement);
		
		return outputDoc;
	}
	
	public static void setIndents(Proof p, int indent) {
		int proofLength = p.getNumSteps();
		
		for(int i = 0; i < proofLength; i++) {
			Step s = p.getStep(i);
			s.setIndent(indent);
			for(int j = 0; j < subProofRules.length; j++) {
				if(s.getRule().equals(subProofRules[j])) {
					if(s.getRule().equals("DISJUNCTION_ELIMINATION")) {
						setIndents(proofs.get(Integer.valueOf(s.getPremise(1))), indent + 1);
						setIndents(proofs.get(Integer.valueOf(s.getPremise(2))), indent + 1);
					} else {
						for(int k = 0; k < s.getNumPremises(); k++) {
							setIndents(proofs.get(Integer.valueOf(s.getPremise(k))), indent + 1);
						}
					}
				}
			}
			steps.add(s);
		}
	}
	
	public static Proof convertProof() {
		Proof newProof = null;
		Iterator<Integer> iter = proofs.keySet().iterator();
		List<Step> newSteps = new ArrayList<Step>();
		
		while(iter.hasNext()) {
			Proof p = proofs.get(iter.next());
			for(int i = 0; i < p.getNumSteps(); i++) {
				steps.add(p.getStep(i));
			}
		}
		Collections.sort(steps, new StepComparer());
		
		for(int i = 0; i < steps.size(); i++) {
			Step step = steps.get(i);
			SequentStep newStep = new SequentStep(step, newSteps, proofs);
			newSteps.add(newStep);
		}
		
		newProof = new Proof(1, newSteps, "Sequent", proofs.get(1).getGoal());
		
		return newProof;
	}
}