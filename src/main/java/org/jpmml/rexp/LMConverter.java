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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.NormDiscrete;
import org.dmg.pmml.OpType;
import org.dmg.pmml.TypeDefinitionField;
import org.dmg.pmml.Value;
import org.dmg.pmml.regression.CategoricalPredictor;
import org.dmg.pmml.regression.NumericPredictor;
import org.dmg.pmml.regression.PredictorTerm;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.WildcardFeature;
import org.jpmml.model.visitors.FieldReferenceFinder;

public class LMConverter extends ModelConverter<RGenericVector> {

	private Map<FieldName, BinaryFeature> binaryFeatures = new LinkedHashMap<>();


	public LMConverter(RGenericVector lm){
		super(lm);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");
		RGenericVector xlevels = (RGenericVector)lm.getValue("xlevels", true);
		RGenericVector model = (RGenericVector)lm.getValue("model");
		RGenericVector data = (RGenericVector)lm.getValue("data", true);

		RExp terms = model.getAttributeValue("terms");

		RIntegerVector factors = (RIntegerVector)terms.getAttributeValue("factors");
		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");
		RStringVector dataClasses = (RStringVector)terms.getAttributeValue("dataClasses");

		RGenericVector dimnames = (RGenericVector)factors.getAttributeValue("dimnames");

		RStringVector variableRows = (RStringVector)dimnames.getValue(0);
		RStringVector termColumns = (RStringVector)dimnames.getValue(1);

		List<TypeDefinitionField> fields = new ArrayList<>();

		Set<FieldName> expressionFieldNames = new LinkedHashSet<>();

		fields:
		for(int i = 0; i < variableRows.size(); i++){
			String variable = variableRows.getValue(i);

			FieldName name = FieldName.create(variable);
			DataType dataType = RExpUtil.getDataType(dataClasses.getValue(variable));

			if(variable.startsWith("I(") && variable.endsWith(")")){
				String string = variable.substring("I(".length(), variable.length() - ")".length());

				Expression expression = ExpressionTranslator.translate(string);

				FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
				fieldReferenceFinder.applyTo(expression);

				expressionFieldNames.addAll(fieldReferenceFinder.getFieldNames());

				DerivedField derivedField =  featureMapper.createDerivedField(name, OpType.CONTINUOUS, dataType, expression);

				fields.add(derivedField);

				continue fields;
			} // End if

			if(xlevels != null && xlevels.hasValue(variable)){
				RStringVector levels = (RStringVector)xlevels.getValue(variable);

				DataField dataField = featureMapper.createDataField(name, OpType.CATEGORICAL, dataType);

				deriveBinaryFields(dataField, levels.getValues(), featureMapper);

				fields.add(dataField);
			} else

			if((DataType.BOOLEAN).equals(dataType)){
				DataField dataField = featureMapper.createDataField(name, OpType.CATEGORICAL, dataType);

				deriveBinaryFields(dataField, Arrays.asList("TRUE", "FALSE"), Arrays.asList("true", "false"), featureMapper);

				fields.add(dataField);
			} else

			{
				DataField dataField = featureMapper.createDataField(name, OpType.CONTINUOUS, dataType);

				fields.add(dataField);
			}
		}

		for(FieldName expressionFieldName : expressionFieldNames){
			DataField dataField = featureMapper.getDataField(expressionFieldName);

			if(dataField == null){
				DataType dataType = DataType.DOUBLE;

				if(data != null){
					RVector<?> column = (RVector<?>)data.getValue(expressionFieldName.getValue());

					dataType = column.getDataType();
				}

				dataField = featureMapper.createDataField(expressionFieldName, OpType.CONTINUOUS, dataType);
			}
		}

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)fields.get(responseIndex - 1);

			Feature feature = new WildcardFeature(dataField);

			featureMapper.append(feature);
		} else

		{
			throw new IllegalArgumentException();
		}

		// Independent variables
		RStringVector coefficientNames = coefficients.names();
		for(int i = 0; i < coefficientNames.size(); i++){
			String coefficientName = coefficientNames.getValue(i);

			if((LMConverter.INTERCEPT).equals(coefficientName)){
				continue;
			}

			Feature feature;

			String[] variables = coefficientName.split(":");
			if(variables.length == 1){
				String variable = variables[0];

				TypeDefinitionField field = featureMapper.getField(FieldName.create(variable));

				feature = new ContinuousFeature(field);
			} else

			{
				List<FieldName> names = new ArrayList<>();

				for(String variable : variables){
					TypeDefinitionField field = featureMapper.getField(FieldName.create(variable));

					names.add(field.getName());
				}

				feature = new InteractionFeature(FieldName.create(coefficientName), DataType.DOUBLE, names);
			}

			featureMapper.append(feature);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");

		Double intercept = coefficients.getValue(LMConverter.INTERCEPT, true);

		List<Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + (intercept != null ? 1 : 0))){
			throw new IllegalArgumentException();
		} // End if

		if(intercept == null){
			intercept = 0d;
		}

		RegressionTable regressionTable = new RegressionTable(intercept);

		for(Feature feature : features){
			double coefficient = coefficients.getValue((feature.getName()).getValue());

			feature = refine(feature);

			if(feature instanceof InteractionFeature){
				InteractionFeature interactionFeature = (InteractionFeature)feature;

				PredictorTerm predictorTerm = new PredictorTerm()
					//.setName(interactionFeature.getName())
					.setCoefficient(coefficient);

				List<FieldName> names = interactionFeature.getNames();
				for(FieldName name : names){
					FieldRef fieldRef = new FieldRef(name);

					predictorTerm.addFieldRefs(fieldRef);
				}

				regressionTable.addPredictorTerms(predictorTerm);
			} else

			if(feature instanceof ContinuousFeature){
				ContinuousFeature continuousFeature = (ContinuousFeature)feature;

				NumericPredictor numericPredictor = new NumericPredictor()
					.setName(continuousFeature.getName())
					.setCoefficient(coefficient);

				regressionTable.addNumericPredictors(numericPredictor);
			} else

			if(feature instanceof BinaryFeature){
				BinaryFeature binaryFeature = (BinaryFeature)feature;

				CategoricalPredictor categoricalPredictor = new CategoricalPredictor()
					.setName(binaryFeature.getName())
					.setValue(binaryFeature.getValue())
					.setCoefficient(coefficient);

				regressionTable.addCategoricalPredictors(categoricalPredictor);
			} else

			{
				throw new IllegalArgumentException();
			}
		}

		RegressionModel regressionModel = new RegressionModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), null)
			.addRegressionTables(regressionTable);

		return regressionModel;
	}

	public Feature refine(Feature feature){
		BinaryFeature binaryFeature = this.binaryFeatures.get(feature.getName());

		if(binaryFeature != null){
			return binaryFeature;
		}

		return feature;
	}

	private void deriveBinaryFields(DataField dataField, List<String> categories, FeatureMapper featureMapper){
		deriveBinaryFields(dataField, categories, categories, featureMapper);
	}

	private void deriveBinaryFields(DataField dataField, List<String> categoryNames, List<String> categoryValues, FeatureMapper featureMapper){
		FieldName name = dataField.getName();

		if(categoryNames.size() != categoryValues.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < categoryNames.size(); i++){
			String categoryName = categoryNames.get(i);
			String categoryValue = categoryValues.get(i);

			NormDiscrete normDiscrete = new NormDiscrete(name, categoryValue);

			DerivedField derivedField = featureMapper.createDerivedField(FieldName.create(name.getValue() + categoryName), OpType.CONTINUOUS, DataType.DOUBLE, normDiscrete);

			BinaryFeature binaryFeature = new BinaryFeature(dataField, categoryValue);

			this.binaryFeatures.put(derivedField.getName(), binaryFeature);
		}

		if(categoryValues.size() > 0){
			List<Value> values = dataField.getValues();

			values.addAll(PMMLUtil.createValues(categoryValues));
		}
	}

	public static final String INTERCEPT = "(Intercept)";
}