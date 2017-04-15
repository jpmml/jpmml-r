/*
 * Copyright (c) 2017 Villu Ruusmann
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

import org.dmg.pmml.Apply;
import org.dmg.pmml.Array;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.RealSparseArray;
import org.dmg.pmml.regression.CategoricalPredictor;
import org.dmg.pmml.support_vector_machine.Coefficient;
import org.dmg.pmml.support_vector_machine.Coefficients;
import org.dmg.pmml.support_vector_machine.LinearKernel;
import org.dmg.pmml.support_vector_machine.PolynomialKernel;
import org.dmg.pmml.support_vector_machine.RadialBasisKernel;
import org.dmg.pmml.support_vector_machine.SigmoidKernel;
import org.dmg.pmml.support_vector_machine.SupportVector;
import org.dmg.pmml.support_vector_machine.SupportVectorMachine;
import org.dmg.pmml.support_vector_machine.SupportVectorMachineModel;
import org.dmg.pmml.support_vector_machine.SupportVectors;
import org.dmg.pmml.support_vector_machine.VectorDictionary;
import org.dmg.pmml.support_vector_machine.VectorFields;
import org.dmg.pmml.support_vector_machine.VectorInstance;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.CMatrixUtil;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.Transformation;
import org.jpmml.converter.ValueUtil;

public class SVMConverter extends ModelConverter<RGenericVector> {

	public SVMConverter(RGenericVector svm){
		super(svm);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RExp terms = svm.getValue("terms", true);

		if(terms != null){
			encodeFormula(encoder);
		} else

		{
			encodeNonFormula(encoder);
		}
	}

	@Override
	public SupportVectorMachineModel encodeModel(Schema schema){
		RGenericVector svm = getObject();

		RDoubleVector type = (RDoubleVector)svm.getValue("type");
		RDoubleVector kernel = (RDoubleVector)svm.getValue("kernel");
		RDoubleVector degree = (RDoubleVector)svm.getValue("degree");
		RDoubleVector gamma = (RDoubleVector)svm.getValue("gamma");
		RDoubleVector coef0 = (RDoubleVector)svm.getValue("coef0");
		RGenericVector yScale = (RGenericVector)svm.getValue("y.scale");
		RIntegerVector nSv = (RIntegerVector)svm.getValue("nSV");
		RDoubleVector sv = (RDoubleVector)svm.getValue("SV");
		RDoubleVector rho = (RDoubleVector)svm.getValue("rho");
		RDoubleVector coefs = (RDoubleVector)svm.getValue("coefs");

		Type svmType = Type.values()[ValueUtil.asInt(type.asScalar())];
		Kernel svmKernel = Kernel.values()[ValueUtil.asInt(kernel.asScalar())];

		SupportVectorMachineModel supportVectorMachineModel;

		switch(svmType){
			case C_CLASSIFICATION:
			case NU_CLASSIFICATION:
				{
					supportVectorMachineModel = encodeClassification(nSv, sv, rho, coefs, schema);
				}
				break;
			case ONE_CLASSIFICATION:
				{
					Transformation outlier = new Transformation(){

						@Override
						public FieldName getName(FieldName name){
							return FieldName.create("outlier");
						}

						@Override
						public DataType getDataType(DataType dataType){
							return DataType.BOOLEAN;
						}

						@Override
						public OpType getOpType(OpType opType){
							return OpType.CATEGORICAL;
						}

						@Override
						public boolean isFinalResult(){
							return true;
						}

						@Override
						public Expression createExpression(FieldRef fieldRef){
							return PMMLUtil.createApply("lessOrEqual", fieldRef, PMMLUtil.createConstant(0d));
						}
					};

					supportVectorMachineModel = encodeRegression(sv, rho, coefs, schema)
						.setOutput(ModelUtil.createPredictedOutput(FieldName.create("decisionFunction"), OpType.CONTINUOUS, DataType.DOUBLE, outlier));
				}
				break;
			case EPS_REGRESSION:
			case NU_REGRESSION:
				{
					supportVectorMachineModel = encodeRegression(sv, rho, coefs, schema);

					if(yScale != null && yScale.size() > 0){
						RDoubleVector yScaledCenter = (RDoubleVector)yScale.getValue("scaled:center");
						RDoubleVector yScaledScale = (RDoubleVector)yScale.getValue("scaled:scale");

						supportVectorMachineModel.setTargets(ModelUtil.createRescaleTargets(schema, -1d * yScaledScale.asScalar(), yScaledCenter.asScalar()));
					}
				}
				break;
			default:
				throw new IllegalArgumentException();
		}

		supportVectorMachineModel.setKernel(svmKernel.encodeKernel(degree.asScalar(), gamma.asScalar(), coef0.asScalar()));

		return supportVectorMachineModel;
	}

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector type = (RDoubleVector)svm.getValue("type");
		RDoubleVector sv = (RDoubleVector)svm.getValue("SV");
		RVector<?> levels = (RVector<?>)svm.getValue("levels");
		RExp terms = svm.getValue("terms");

		final
		RGenericVector xlevels;

		try {
			xlevels = (RGenericVector)svm.getValue("xlevels");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No variable levels information. Please initialize the \'xlevels\' attribute", iae);
		}

		Type svmType = Type.values()[ValueUtil.asInt(type.asScalar())];

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");

		FormulaContext context = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){

				if(xlevels.hasValue(variable)){
					RStringVector levels = (RStringVector)xlevels.getValue(variable);

					return levels.getValues();
				}

				return null;
			}

			@Override
			public RGenericVector getData(){
				return null;
			}
		};

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			switch(svmType){
				case C_CLASSIFICATION:
				case NU_CLASSIFICATION:
					{
						RStringVector stringLevels = (RStringVector)levels;

						dataField.setOpType(OpType.CATEGORICAL);

						PMMLUtil.addValues(dataField, stringLevels.getValues());
					}
					break;
				case ONE_CLASSIFICATION:
					{
						OpType opType = dataField.getOpType();

						if(!(OpType.CONTINUOUS).equals(opType)){
							throw new IllegalArgumentException();
						}
					}
					break;
				default:
					break;
			}

			encoder.setLabel(dataField);
		} else

		{
			switch(svmType){
				case ONE_CLASSIFICATION:
					break;
				default:
					throw new IllegalArgumentException();
			}

			encoder.setLabel(new ContinuousLabel(null, DataType.DOUBLE));
		}

		List<Feature> features = new ArrayList<>();

		// Independent variables
		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			Feature feature = formula.resolveFeature(columnName);

			features.add(feature);
		}

		features = scale(features, encoder);

		for(Feature feature : features){
			encoder.addFeature(feature);
		}
	}

	private void encodeNonFormula(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector type = (RDoubleVector)svm.getValue("type");
		RDoubleVector sv = (RDoubleVector)svm.getValue("SV");
		RVector<?> levels = (RVector<?>)svm.getValue("levels");

		Type svmType = Type.values()[ValueUtil.asInt(type.asScalar())];

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		// Dependent variable
		{
			FieldName name = FieldName.create("_target");

			switch(svmType){
				case C_CLASSIFICATION:
				case NU_CLASSIFICATION:
					{
						RStringVector stringLevels = (RStringVector)levels;

						DataField dataField = encoder.createDataField(name, OpType.CATEGORICAL, DataType.STRING, stringLevels.getValues());

						encoder.setLabel(dataField);
					}
					break;
				case ONE_CLASSIFICATION:
					{
						encoder.setLabel(new ContinuousLabel(null, DataType.DOUBLE));
					}
					break;
				case EPS_REGRESSION:
				case NU_REGRESSION:
					{
						DataField dataField = encoder.createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);

						encoder.setLabel(dataField);
					}
					break;
			}
		}

		List<Feature> features = new ArrayList<>();

		// Independent variables
		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			DataField dataField = encoder.createDataField(FieldName.create(columnName), OpType.CONTINUOUS, DataType.DOUBLE);

			features.add(new ContinuousFeature(encoder, dataField));
		}

		features = scale(features, encoder);

		for(Feature feature : features){
			encoder.addFeature(feature);
		}
	}

	private List<Feature> scale(List<Feature> features, RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector sv = (RDoubleVector)svm.getValue("SV");
		RBooleanVector scaled = (RBooleanVector)svm.getValue("scaled");
		RGenericVector xScale = (RGenericVector)svm.getValue("x.scale");

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		if((scaled.size() != columnNames.size()) || (scaled.size() != features.size())){
			throw new IllegalArgumentException();
		}

		RDoubleVector xScaledCenter = null;
		RDoubleVector xScaledScale = null;

		if(xScale != null){
			xScaledCenter = (RDoubleVector)xScale.getValue("scaled:center");
			xScaledScale = (RDoubleVector)xScale.getValue("scaled:scale");
		}

		List<Feature> result = new ArrayList<>();

		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);
			Feature feature = features.get(i);

			if(scaled.getValue(i)){
				feature = feature.toContinuousFeature();

				FieldName name = FieldName.create("scale(" + (feature.getName()).getValue() + ")");

				DerivedField derivedField = encoder.getDerivedField(name);
				if(derivedField == null){
					Double center = xScaledCenter.getValue(columnName);
					Double scale = xScaledScale.getValue(columnName);

					Apply apply = PMMLUtil.createApply("/", PMMLUtil.createApply("-", feature.ref(), PMMLUtil.createConstant(center)), PMMLUtil.createConstant(scale));

					derivedField = encoder.createDerivedField(name, OpType.CONTINUOUS, DataType.DOUBLE, apply);
				}

				feature = new ContinuousFeature(encoder, derivedField);
			}

			result.add(feature);
		}

		return result;
	}

	static
	private SupportVectorMachineModel encodeClassification(RIntegerVector nSv, RDoubleVector sv, RDoubleVector rho, RDoubleVector coefs, Schema schema){
		VectorDictionary vectorDictionary = encodeVectorDictionary(sv, schema);

		List<VectorInstance> vectorInstances = vectorDictionary.getVectorInstances();

		int numberOfVectors = 0;

		int[] offsets = new int[nSv.size() + 1];

		for(int i = 0; i < nSv.size(); i++){
			numberOfVectors += nSv.getValue(i);

			offsets[i + 1] = offsets[i] + nSv.getValue(i);
		}

		List<SupportVectorMachine> supportVectorMachines = new ArrayList<>();

		int i = 0;

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		for(int first = 0, size = categoricalLabel.size(); first < size; first++){

			for(int second = first + 1; second < size; second++){
				List<VectorInstance> svmVectorInstances = new ArrayList<>();
				svmVectorInstances.addAll(slice(vectorInstances, offsets, first));
				svmVectorInstances.addAll(slice(vectorInstances, offsets, second));

				Double svmRho = rho.getValue(i);

				List<Double> svmCoefs = new ArrayList<>();
				svmCoefs.addAll(slice(CMatrixUtil.getRow(coefs.getValues(), size - 1, numberOfVectors, second - 1), offsets, first));
				svmCoefs.addAll(slice(CMatrixUtil.getRow(coefs.getValues(), size - 1, numberOfVectors, first), offsets, second));

				SupportVectorMachine supportVectorMachine = encodeSupportVectorMachine(svmVectorInstances, svmRho, svmCoefs)
					.setTargetCategory(categoricalLabel.getValue(first))
					.setAlternateTargetCategory(categoricalLabel.getValue(second));

				supportVectorMachines.add(supportVectorMachine);

				i++;
			}
		}

		SupportVectorMachineModel supportVectorMachineModel = new SupportVectorMachineModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(schema), vectorDictionary, supportVectorMachines)
			.setClassificationMethod(SupportVectorMachineModel.ClassificationMethod.ONE_AGAINST_ONE);

		return supportVectorMachineModel;
	}

	static
	private SupportVectorMachineModel encodeRegression(RDoubleVector sv, RDoubleVector rho, RDoubleVector coefs, Schema schema){
		VectorDictionary vectorDictionary = encodeVectorDictionary(sv, schema);

		List<VectorInstance> vectorInstances = vectorDictionary.getVectorInstances();

		List<SupportVectorMachine> supportVectorMachines = new ArrayList<>();
		supportVectorMachines.add(encodeSupportVectorMachine(vectorInstances, rho.asScalar(), coefs.getValues()));

		SupportVectorMachineModel supportvectorMachineModel = new SupportVectorMachineModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), vectorDictionary, supportVectorMachines);

		return supportvectorMachineModel;
	}

	static
	private VectorDictionary encodeVectorDictionary(RDoubleVector sv, Schema schema){
		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		List<Feature> features = schema.getFeatures();

		if(columnNames.size() != features.size()){
			throw new IllegalArgumentException();
		}

		VectorFields vectorFields = new VectorFields();

		for(Feature feature : features){

			if(feature instanceof BinaryFeature){
				BinaryFeature binaryFeature = (BinaryFeature)feature;

				CategoricalPredictor categoricalPredictor = new CategoricalPredictor(binaryFeature.getName(), binaryFeature.getValue(), 1d);

				vectorFields.addContent(categoricalPredictor);
			} else

			{
				ContinuousFeature continuousFeature = feature.toContinuousFeature();

				vectorFields.addContent(continuousFeature.ref());
			}
		}

		VectorDictionary vectorDictionary = new VectorDictionary(vectorFields);

		Double defaultValue = 0d;

		for(int i = 0; i < rowNames.size(); i++){
			String rowName = rowNames.getValue(i);

			VectorInstance vectorInstance = new VectorInstance(rowName);

			List<Double> values = FortranMatrixUtil.getRow(sv.getValues(), rowNames.size(), columnNames.size(), i);

			if(ValueUtil.isSparse(values, defaultValue, 0.75d)){
				RealSparseArray sparseArray = PMMLUtil.createRealSparseArray(values, defaultValue);

				vectorInstance.setRealSparseArray(sparseArray);
			} else

			{
				Array array = PMMLUtil.createRealArray(values);

				vectorInstance.setArray(array);
			}

			vectorDictionary.addVectorInstances(vectorInstance);
		}

		return vectorDictionary;
	}

	static
	private SupportVectorMachine encodeSupportVectorMachine(List<VectorInstance> vectorInstances, Double rho, List<Double> coefs){

		if(vectorInstances.size() != coefs.size()){
			throw new IllegalArgumentException();
		}

		Coefficients coefficients = new Coefficients()
			.setAbsoluteValue(rho);

		SupportVectors supportVectors = new SupportVectors();

		for(int i = 0; i < vectorInstances.size(); i++){
			Double coef = coefs.get(i);
			VectorInstance vectorInstance = vectorInstances.get(i);

			Coefficient coefficient = new Coefficient()
				.setValue(-1d * coef);

			coefficients.addCoefficients(coefficient);

			SupportVector supportVector = new SupportVector(vectorInstance.getId());

			supportVectors.addSupportVectors(supportVector);
		}

		SupportVectorMachine supportVectorMachine = new SupportVectorMachine(coefficients)
			.setSupportVectors(supportVectors);

		return supportVectorMachine;
	}

	static
	private <E> List<E> slice(List<E> list, int[] offsets, int index){
		return list.subList(offsets[index], offsets[index + 1]);
	}

	private enum Type {
		C_CLASSIFICATION,
		NU_CLASSIFICATION,
		ONE_CLASSIFICATION,
		EPS_REGRESSION,
		NU_REGRESSION,
		;
	}

	private enum Kernel {
		LINEAR(){

			@Override
			public LinearKernel encodeKernel(Double degree, Double gamma, Double coef0){
				return new LinearKernel();
			}
		},
		POLYNOMIAL(){

			@Override
			public PolynomialKernel encodeKernel(Double degree, Double gamma, Double coef0){
				return new PolynomialKernel()
					.setGamma(gamma)
					.setCoef0(coef0)
					.setDegree(degree);
			}
		},
		RADIAL(){

			@Override
			public RadialBasisKernel encodeKernel(Double degree, Double gamma, Double coef0){
				return new RadialBasisKernel()
					.setGamma(gamma);
			}
		},
		SIGMOID(){

			@Override
			public SigmoidKernel encodeKernel(Double degree, Double gamma, Double coef0){
				return new SigmoidKernel()
					.setGamma(gamma)
					.setCoef0(coef0);
			}
		},
		;

		abstract
		public org.dmg.pmml.support_vector_machine.Kernel encodeKernel(Double degree, Double gamma, Double coef0);
	}
}