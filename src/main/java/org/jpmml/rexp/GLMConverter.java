/*
 * Copyright (c) 2016 Villu Ruusmann
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Value;
import org.dmg.pmml.general_regression.CovariateList;
import org.dmg.pmml.general_regression.FactorList;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.dmg.pmml.general_regression.PCell;
import org.dmg.pmml.general_regression.PPCell;
import org.dmg.pmml.general_regression.PPMatrix;
import org.dmg.pmml.general_regression.ParamMatrix;
import org.dmg.pmml.general_regression.Parameter;
import org.dmg.pmml.general_regression.ParameterList;
import org.dmg.pmml.general_regression.Predictor;
import org.dmg.pmml.general_regression.PredictorList;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;

public class GLMConverter extends LMConverter {

	public GLMConverter(RGenericVector glm){
		super(glm);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector glm = getObject();

		RGenericVector family = (RGenericVector)glm.getValue("family");
		RGenericVector model = (RGenericVector)glm.getValue("model");

		RStringVector familyFamily = (RStringVector)family.getValue("family");

		super.encodeFeatures(featureMapper);

		GeneralRegressionModel.Distribution distribution = parseFamily(familyFamily.asScalar());
		switch(distribution){
			case BINOMIAL:
				DataField dataField = featureMapper.getTargetField();

				dataField.setOpType(OpType.CATEGORICAL);

				RNumberVector<?> variable = (RNumberVector<?>)model.getValue((dataField.getName()).getValue());
				if(!(variable instanceof RIntegerVector)){
					throw new IllegalArgumentException();
				}

				RIntegerVector factor = (RIntegerVector)variable;

				List<Value> values = dataField.getValues();
				if(values.size() > 0){
					throw new IllegalArgumentException();
				}

				values.addAll(PMMLUtil.createValues(factor.getLevelValues()));
				break;
			default:
				break;
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector glm = getObject();

		RDoubleVector coefficients = (RDoubleVector)glm.getValue("coefficients");
		RGenericVector family = (RGenericVector)glm.getValue("family");

		Double intercept = coefficients.getValue(LMConverter.INTERCEPT, true);

		RStringVector familyFamily = (RStringVector)family.getValue("family");
		RStringVector familyLink = (RStringVector)family.getValue("link");

		List<Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + (intercept != null ? 1 : 0))){
			throw new IllegalArgumentException();
		}

		String targetCategory = null;

		List<String> targetCategories = schema.getTargetCategories();
		if(targetCategories != null && targetCategories.size() > 0){

			if(targetCategories.size() != 2){
				throw new IllegalArgumentException();
			}

			targetCategory = targetCategories.get(1);
		}

		ParameterList parameterList = new ParameterList();

		PPMatrix ppMatrix = new PPMatrix();

		ParamMatrix paramMatrix = new ParamMatrix();

		if(intercept != null){
			Parameter parameter = new Parameter("p0")
				.setLabel("(intercept)");

			parameterList.addParameters(parameter);

			PCell pCell = new PCell(parameter.getName(), intercept)
				.setTargetCategory(targetCategory);

			paramMatrix.addPCells(pCell);
		}

		Set<FieldName> covariates = new LinkedHashSet<>();

		Set<FieldName> factors = new LinkedHashSet<>();

		for(int i = 0; i < features.size(); i++){
			Feature feature = features.get(i);

			double coefficient = coefficients.getValue((feature.getName()).getValue());

			feature = refine(feature);

			Parameter parameter = new Parameter("p" + String.valueOf(i + 1));

			parameterList.addParameters(parameter);

			List<Feature> simpleFeatures = expandInteractionFeatures(feature);
			for(Feature simpleFeature : simpleFeatures){
				PPCell ppCell;

				if(simpleFeature instanceof ContinuousFeature){
					ContinuousFeature continuousFeature = (ContinuousFeature)simpleFeature;

					covariates.add(continuousFeature.getName());

					ppCell = new PPCell("1", continuousFeature.getName(), parameter.getName());
				} else

				if(simpleFeature instanceof BinaryFeature){
					BinaryFeature binaryFeature = (BinaryFeature)simpleFeature;

					factors.add(binaryFeature.getName());

					ppCell = new PPCell(binaryFeature.getValue(), binaryFeature.getName(), parameter.getName());
				} else

				{
					throw new IllegalArgumentException();
				}

				ppMatrix.addPPCells(ppCell);
			}

			PCell pCell = new PCell(parameter.getName(), coefficient)
				.setTargetCategory(targetCategory);

			paramMatrix.addPCells(pCell);
		}

		MiningFunction miningFunction = (targetCategory != null ? MiningFunction.CLASSIFICATION : MiningFunction.REGRESSION);

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, miningFunction, ModelUtil.createMiningSchema(schema), parameterList, ppMatrix, paramMatrix)
			.setDistribution(parseFamily(familyFamily.asScalar()))
			.setLinkFunction(parseLinkFunction(familyLink.asScalar()))
			.setLinkParameter(parseLinkParameter(familyLink.asScalar()))
			.setCovariateList(createPredictorList(new CovariateList(), covariates))
			.setFactorList(createPredictorList(new FactorList(), factors));

		switch(miningFunction){
			case CLASSIFICATION:
				generalRegressionModel.setOutput(ModelUtil.createProbabilityOutput(schema));
				break;
			default:
				break;
		}

		return generalRegressionModel;
	}

	private List<Feature> expandInteractionFeatures(Feature feature){

		if(feature instanceof InteractionFeature){
			InteractionFeature interactionFeature = (InteractionFeature)feature;

			List<Feature> result = new ArrayList<>();

			List<FieldName> names = interactionFeature.getNames();
			for(FieldName name : names){
				Feature simpleFeature = new ContinuousFeature(name, DataType.DOUBLE); // XXX

				simpleFeature = refine(simpleFeature);

				result.add(simpleFeature);
			}

			return result;
		}

		return Collections.singletonList(feature);
	}

	static
	private GeneralRegressionModel.Distribution parseFamily(String family){

		switch(family){
			case "binomial":
				return GeneralRegressionModel.Distribution.BINOMIAL;
			case "gaussian":
				return GeneralRegressionModel.Distribution.NORMAL;
			case "Gamma":
				return GeneralRegressionModel.Distribution.GAMMA;
			case "inverse.gaussian":
				return GeneralRegressionModel.Distribution.IGAUSS;
			case "poisson":
				return GeneralRegressionModel.Distribution.POISSON;
			default:
				throw new IllegalArgumentException(family);
		}
	}

	static
	private GeneralRegressionModel.LinkFunction parseLinkFunction(String link){

		switch(link){
			case "cloglog":
				return GeneralRegressionModel.LinkFunction.CLOGLOG;
			case "identity":
				return GeneralRegressionModel.LinkFunction.IDENTITY;
			case "inverse":
				return GeneralRegressionModel.LinkFunction.POWER;
			case "log":
				return GeneralRegressionModel.LinkFunction.LOG;
			case "logit":
				return GeneralRegressionModel.LinkFunction.LOGIT;
			case "probit":
				return GeneralRegressionModel.LinkFunction.PROBIT;
			case "sqrt":
				return GeneralRegressionModel.LinkFunction.POWER;
			default:
				throw new IllegalArgumentException(link);
		}
	}

	static
	private Double parseLinkParameter(String link){

		switch(link){
			case "inverse":
				return -1d;
			case "sqrt":
				return (1d / 2d);
			default:
				return null;
		}
	}

	static
	private <L extends PredictorList> L createPredictorList(L predictorList, Set<FieldName> names){

		if(names.isEmpty()){
			return null;
		}

		List<Predictor> predictors = predictorList.getPredictors();

		for(FieldName name : names){
			String value = name.getValue();

			Predictor predictor = new Predictor(name);

			predictors.add(predictor);
		}

		return predictorList;
	}
}