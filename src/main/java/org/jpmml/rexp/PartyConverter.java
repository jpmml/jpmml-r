/*
 * Copyright (c) 2018 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class PartyConverter extends TreeModelConverter<RGenericVector> {

	public PartyConverter(RGenericVector party){
		super(party);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector party = getObject();

		RGenericVector data = (RGenericVector)party.getValue("data");
		RExp terms = party.getValue("terms");

		RIntegerVector factors = (RIntegerVector)terms.getAttributeValue("factors");
		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		RStringVector variableRows = factors.dimnames(0);
		RStringVector termColumns = factors.dimnames(1);

		FormulaContext context = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){
				RVector<?> vector = (RVector<?>)data.getValue(variable);

				if(RExpUtil.isFactor(vector)){
					RIntegerVector factor = (RIntegerVector)vector;

					return factor.getLevelValues();
				}

				return null;
			}

			@Override
			public RGenericVector getData(){
				return null;
			}
		};

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		RIntegerVector levels = null;

		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			RVector<?> vector = (RVector<?>)data.getValue(termColumns.getValue(responseIndex - 1));

			if(RExpUtil.isFactor(vector)){
				levels = (RIntegerVector)vector;
			}
		} else

		{
			throw new IllegalArgumentException();
		}

		SchemaUtil.setLabel(formula, terms, levels, encoder);

		SchemaUtil.addFeatures(formula, termColumns, false, encoder);
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector party = getObject();

		RGenericVector partyNode = (RGenericVector)party.getValue("node");

		RVector<?> scores;

		try {
			scores = (RVector<?>)party.getValue("scores");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No node scores information. Please initialize the \'scores\' element", iae);
		}

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, partyNode, scores, schema);

		MiningFunction miningFunction;

		if(RExpUtil.isFactor(scores)){
			miningFunction = MiningFunction.CLASSIFICATION;
		} else

		{
			miningFunction = MiningFunction.REGRESSION;
		}

		TreeModel treeModel = new TreeModel(miningFunction, ModelUtil.createMiningSchema(schema.getLabel()), root);

		return treeModel;
	}

	private void encodeNode(Node node, RGenericVector partyNode, RVector<?> scores, Schema schema){
		RIntegerVector id = (RIntegerVector)partyNode.getValue("id");
		RGenericVector split = (RGenericVector)partyNode.getValue("split");
		RGenericVector kids = (RGenericVector)partyNode.getValue("kids");
		RGenericVector surrogates = (RGenericVector)partyNode.getValue("surrogates");
		RGenericVector info = (RGenericVector)partyNode.getValue("info");

		if(surrogates != null){
			throw new IllegalArgumentException();
		}

		node.setId(String.valueOf(id.asScalar()));

		if(RExpUtil.isFactor(scores)){
			RIntegerVector factor = (RIntegerVector)scores;

			node.setScore(factor.getLevelValue(id.asScalar() - 1));
		} else

		{
			node.setScore(ValueUtil.formatValue(scores.getValue(id.asScalar() - 1)));
		} // End if

		if(kids == null){
			return;
		} // End if

		if(kids.size() != 2){
			throw new IllegalArgumentException();
		}

		RIntegerVector varid = (RIntegerVector)split.getValue("varid");
		RDoubleVector breaks = (RDoubleVector)split.getValue("breaks");
		RIntegerVector index = (RIntegerVector)split.getValue("index");
		RBooleanVector right = (RBooleanVector)split.getValue("right");

		List<? extends Feature> features = schema.getFeatures();

		Feature feature = features.get(varid.asScalar() - 1);

		Predicate leftPredicate;
		Predicate rightPredicate;

		if(breaks != null && index == null){
			ContinuousFeature continuousFeature = (ContinuousFeature)feature;

			if(breaks.size() != 1){
				throw new IllegalArgumentException();
			}

			String value = ValueUtil.formatValue(breaks.asScalar());

			if(right.asScalar()){
				leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_OR_EQUAL, value);
				rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_THAN, value);
			} else

			{
				leftPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.LESS_THAN, value);
				rightPredicate = createSimplePredicate(continuousFeature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);
			}
		} else

		if(breaks == null && index != null){
			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			List<String> values = categoricalFeature.getValues();

			if(right.asScalar()){
				leftPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, index, 1));
				rightPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, index, 2));
			} else

			{
				throw new IllegalArgumentException();
			}
		} else

		{
			throw new IllegalArgumentException();
		}

		Node leftChild = new Node()
			.setPredicate(leftPredicate);

		Node rightChild = new Node()
			.setPredicate(rightPredicate);

		encodeNode(leftChild, (RGenericVector)kids.getValue(0), scores, schema);
		encodeNode(rightChild, (RGenericVector)kids.getValue(1), scores, schema);

		node.addNodes(leftChild, rightChild);
	}

	static
	private List<String> selectValues(List<String> values, RIntegerVector index, int flag){
		List<String> result = new ArrayList<>();

		if(values.size() != index.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < values.size(); i++){
			String value = values.get(i);

			if(index.getValue(i) == flag){
				result.add(value);
			}
		}

		return result;
	}
}