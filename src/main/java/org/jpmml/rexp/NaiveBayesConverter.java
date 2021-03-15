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

import java.util.List;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.GaussianDistribution;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.naive_bayes.BayesInput;
import org.dmg.pmml.naive_bayes.BayesInputs;
import org.dmg.pmml.naive_bayes.BayesOutput;
import org.dmg.pmml.naive_bayes.NaiveBayesModel;
import org.dmg.pmml.naive_bayes.PairCounts;
import org.dmg.pmml.naive_bayes.TargetValueCount;
import org.dmg.pmml.naive_bayes.TargetValueCounts;
import org.dmg.pmml.naive_bayes.TargetValueStat;
import org.dmg.pmml.naive_bayes.TargetValueStats;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;

public class NaiveBayesConverter extends ModelConverter<RGenericVector> {

	public NaiveBayesConverter(RGenericVector naiveBayes){
		super(naiveBayes);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector naiveBayes = getObject();

		RGenericVector tables = naiveBayes.getGenericElement("tables");
		RStringVector levels = naiveBayes.getStringElement("levels");

		{
			DataField dataField = encoder.createDataField(FieldName.create("_target"), OpType.CATEGORICAL, DataType.STRING, levels.getValues());

			encoder.setLabel(dataField);
		}

		RStringVector tableNames = tables.names();

		for(int i = 0; i < tables.size(); i++){
			RDoubleVector table = tables.getDoubleValue(i);

			RStringVector tableRows = table.dimnames(0);
			RStringVector tableColumns = table.dimnames(1);

			FieldName name = FieldName.create(tableNames.getValue(i));

			DataField dataField;

			if(tableColumns != null){
				dataField = encoder.createDataField(name, OpType.CATEGORICAL, DataType.STRING, tableColumns.getValues());
			} else

			{
				dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			encoder.addFeature(dataField);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector naiveBayes = getObject();

		RIntegerVector apriori = naiveBayes.getIntegerElement("apriori");
		RGenericVector tables = naiveBayes.getGenericElement("tables");

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		BayesInputs bayesInputs = new BayesInputs();

		for(int i = 0; i < features.size(); i++){
			Feature feature = features.get(i);

			FieldName name = feature.getName();

			RDoubleVector table = tables.getDoubleElement(name.getValue());

			RStringVector tableRows = table.dimnames(0);
			RStringVector tableColumns = table.dimnames(1);

			BayesInput bayesInput = new BayesInput(name, null, null);

			if(feature instanceof CategoricalFeature){
				CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

				for(int column = 0; column < tableColumns.size(); column++){
					TargetValueCounts targetValueCounts = new TargetValueCounts();

					List<Double> probabilities = FortranMatrixUtil.getColumn(table.getValues(), tableRows.size(), tableColumns.size(), column);

					for(int row = 0; row < tableRows.size(); row++){
						double count = apriori.getValue(row) * probabilities.get(row);

						TargetValueCount targetValueCount = new TargetValueCount(tableRows.getValue(row), count);

						targetValueCounts.addTargetValueCounts(targetValueCount);
					}

					PairCounts pairCounts = new PairCounts(tableColumns.getValue(column), targetValueCounts);

					bayesInput.addPairCounts(pairCounts);
				}
			} else

			if(feature instanceof ContinuousFeature){
				ContinuousFeature continuousFeature = (ContinuousFeature)feature;

				TargetValueStats targetValueStats = new TargetValueStats();

				for(int row = 0; row < tableRows.size(); row++){
					List<Double> stats = FortranMatrixUtil.getRow(table.getValues(), tableRows.size(), 2, row);

					double mean = stats.get(0);
					double variance = Math.pow(stats.get(1), 2);

					TargetValueStat targetValueStat = new TargetValueStat(tableRows.getValue(row), new GaussianDistribution(mean, variance));

					targetValueStats.addTargetValueStats(targetValueStat);
				}

				bayesInput.setTargetValueStats(targetValueStats);
			} else

			{
				throw new IllegalArgumentException();
			}

			bayesInputs.addBayesInputs(bayesInput);
		}

		BayesOutput bayesOutput = new BayesOutput()
			.setField(categoricalLabel.getName());

		{
			TargetValueCounts targetValueCounts = new TargetValueCounts();

			RStringVector aprioriRows = apriori.dimnames(0);

			for(int row = 0; row < aprioriRows.size(); row++){
				int count = apriori.getValue(row);

				TargetValueCount targetValueCount = new TargetValueCount(aprioriRows.getValue(row), count);

				targetValueCounts.addTargetValueCounts(targetValueCount);
			}

			bayesOutput.setTargetValueCounts(targetValueCounts);
		}

		NaiveBayesModel naiveBayesModel = new NaiveBayesModel(0d, MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), bayesInputs, bayesOutput)
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		return naiveBayesModel;
	}
}