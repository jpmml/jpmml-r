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

import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.dmg.pmml.Apply;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.OpType;
import org.dmg.pmml.support_vector_machine.LinearKernel;
import org.dmg.pmml.support_vector_machine.PolynomialKernel;
import org.dmg.pmml.support_vector_machine.RadialBasisKernel;
import org.dmg.pmml.support_vector_machine.SigmoidKernel;
import org.dmg.pmml.support_vector_machine.SupportVectorMachineModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FeatureUtil;
import org.jpmml.converter.FortranMatrix;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.OutlierTransformation;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.Transformation;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.support_vector_machine.LibSVMUtil;

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
					supportVectorMachineModel = encodeClassification(sv, nSv, rho, coefs, schema);
				}
				break;
			case ONE_CLASSIFICATION:
				{
					Transformation outlier = new OutlierTransformation(){

						@Override
						public Expression createExpression(FieldRef fieldRef){
							return PMMLUtil.createApply("lessOrEqual", fieldRef, PMMLUtil.createConstant(0d));
						}
					};

					supportVectorMachineModel = encodeRegression(sv, rho, coefs, schema)
						.setOutput(ModelUtil.createPredictedOutput(FieldName.create("decisionFunction"), OpType.CONTINUOUS, DataType.DOUBLE, outlier));

					if(yScale != null && yScale.size() > 0){
						throw new IllegalArgumentException();
					}
				}
				break;
			case EPS_REGRESSION:
			case NU_REGRESSION:
				{
					supportVectorMachineModel = encodeRegression(sv, rho, coefs, schema);

					if(yScale != null && yScale.size() > 0){
						RDoubleVector yScaledCenter = (RDoubleVector)yScale.getValue("scaled:center");
						RDoubleVector yScaledScale = (RDoubleVector)yScale.getValue("scaled:scale");

						supportVectorMachineModel.setTargets(ModelUtil.createRescaleTargets(-1d * yScaledScale.asScalar(), yScaledCenter.asScalar(), (ContinuousLabel)schema.getLabel()));
					}
				}
				break;
			default:
				throw new IllegalArgumentException();
		}

		supportVectorMachineModel.setKernel(svmKernel.createKernel(degree.asScalar(), gamma.asScalar(), coef0.asScalar()));

		return supportVectorMachineModel;
	}

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector type = (RDoubleVector)svm.getValue("type");
		RDoubleVector sv = (RDoubleVector)svm.getValue("SV");
		RVector<?> levels = (RVector<?>)svm.getValue("levels");
		RExp terms = svm.getValue("terms");

		RGenericVector xlevels;

		try {
			xlevels = (RGenericVector)svm.getValue("xlevels");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No variable levels information. Please initialize the \'xlevels\' element", iae);
		}

		Type svmType = Type.values()[ValueUtil.asInt(type.asScalar())];

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		FormulaContext context = new XLevelsFormulaContext(xlevels);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		// Dependent variable
		switch(svmType){
			case C_CLASSIFICATION:
			case NU_CLASSIFICATION:
				SchemaUtil.setLabel(formula, terms, levels, encoder);
				break;
			case ONE_CLASSIFICATION:
				encoder.setLabel(new ContinuousLabel(null, DataType.DOUBLE));
				break;
			case EPS_REGRESSION:
			case NU_REGRESSION:
				SchemaUtil.setLabel(formula, terms, null, encoder);
				break;
		}

		// Independent variables
		SchemaUtil.addFeatures(formula, columnNames, true, encoder);

		scaleFeatures(encoder);
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

		// Independent variables
		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			DataField dataField = encoder.createDataField(FieldName.create(columnName), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.addFeature(dataField);
		}

		scaleFeatures(encoder);
	}

	private void scaleFeatures(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector sv = (RDoubleVector)svm.getValue("SV");
		RBooleanVector scaled = (RBooleanVector)svm.getValue("scaled");
		RGenericVector xScale = (RGenericVector)svm.getValue("x.scale");

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		List<Feature> features = encoder.getFeatures();

		if((scaled.size() != columnNames.size()) || (scaled.size() != features.size())){
			throw new IllegalArgumentException();
		}

		RDoubleVector xScaledCenter = null;
		RDoubleVector xScaledScale = null;

		if(xScale != null){
			xScaledCenter = (RDoubleVector)xScale.getValue("scaled:center");
			xScaledScale = (RDoubleVector)xScale.getValue("scaled:scale");
		}

		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			if(scaled.getValue(i)){
				Feature feature = (features.get(i)).toContinuousFeature();

				FieldName name = FeatureUtil.createName("scale", feature);

				DerivedField derivedField = encoder.getDerivedField(name);
				if(derivedField == null){
					Double center = xScaledCenter.getValue(columnName);
					Double scale = xScaledScale.getValue(columnName);

					Apply apply = PMMLUtil.createApply("/", PMMLUtil.createApply("-", feature.ref(), PMMLUtil.createConstant(center)), PMMLUtil.createConstant(scale));

					derivedField = encoder.createDerivedField(name, OpType.CONTINUOUS, DataType.DOUBLE, apply);
				}

				features.set(i, new ContinuousFeature(encoder, derivedField));
			}
		}
	}

	static
	private SupportVectorMachineModel encodeClassification(RDoubleVector sv, RIntegerVector nSv, RDoubleVector rho, RDoubleVector coefs, Schema schema){
		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		return LibSVMUtil.createClassification(new FortranMatrix<>(sv.getValues(), rowNames.size(), columnNames.size()), nSv.getValues(), rowNames.getValues(), rho.getValues(), Lists.transform(coefs.getValues(), SVMConverter.FUNCTION_NEGATE), schema);
	}

	static
	private SupportVectorMachineModel encodeRegression(RDoubleVector sv, RDoubleVector rho, RDoubleVector coefs, Schema schema){
		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		return LibSVMUtil.createRegression(new FortranMatrix<>(sv.getValues(), rowNames.size(), columnNames.size()), rowNames.getValues(), rho.asScalar(), Lists.transform(coefs.getValues(), SVMConverter.FUNCTION_NEGATE), schema);
	}

	private static final Function<Double, Double> FUNCTION_NEGATE = new Function<Double, Double>(){

		@Override
		public Double apply(Double value){
			return -1d * value;
		}
	};

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
			public LinearKernel createKernel(Double degree, Double gamma, Double coef0){
				return new LinearKernel();
			}
		},
		POLYNOMIAL(){

			@Override
			public PolynomialKernel createKernel(Double degree, Double gamma, Double coef0){
				return new PolynomialKernel()
					.setGamma(gamma)
					.setCoef0(coef0)
					.setDegree(degree);
			}
		},
		RADIAL(){

			@Override
			public RadialBasisKernel createKernel(Double degree, Double gamma, Double coef0){
				return new RadialBasisKernel()
					.setGamma(gamma);
			}
		},
		SIGMOID(){

			@Override
			public SigmoidKernel createKernel(Double degree, Double gamma, Double coef0){
				return new SigmoidKernel()
					.setGamma(gamma)
					.setCoef0(coef0);
			}
		},
		;

		abstract
		public org.dmg.pmml.support_vector_machine.Kernel createKernel(Double degree, Double gamma, Double coef0);
	}
}
