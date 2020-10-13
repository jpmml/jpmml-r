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
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.support_vector_machine.LinearKernel;
import org.dmg.pmml.support_vector_machine.PolynomialKernel;
import org.dmg.pmml.support_vector_machine.RadialBasisKernel;
import org.dmg.pmml.support_vector_machine.SigmoidKernel;
import org.dmg.pmml.support_vector_machine.SupportVectorMachineModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
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

		if(svm.hasElement("terms")){
			encodeFormula(encoder);
		} else

		{
			encodeNonFormula(encoder);
		}
	}

	@Override
	public SupportVectorMachineModel encodeModel(Schema schema){
		RGenericVector svm = getObject();

		RDoubleVector type = svm.getDoubleElement("type");
		RDoubleVector kernel = svm.getDoubleElement("kernel");
		RDoubleVector degree = svm.getDoubleElement("degree");
		RDoubleVector gamma = svm.getDoubleElement("gamma");
		RDoubleVector coef0 = svm.getDoubleElement("coef0");
		RGenericVector yScale = svm.getGenericElement("y.scale");
		RIntegerVector nSv = svm.getIntegerElement("nSV");
		RDoubleVector sv = svm.getDoubleElement("SV");
		RDoubleVector rho = svm.getDoubleElement("rho");
		RDoubleVector coefs = svm.getDoubleElement("coefs");

		Type svmType = Type.values()[ValueUtil.asInt(type.asScalar())];
		Kernel svmKernel = Kernel.values()[ValueUtil.asInt(kernel.asScalar())];

		org.dmg.pmml.support_vector_machine.Kernel pmmlKernel = svmKernel.createKernel(degree.asScalar(), gamma.asScalar(), coef0.asScalar());

		SupportVectorMachineModel supportVectorMachineModel;

		switch(svmType){
			case C_CLASSIFICATION:
			case NU_CLASSIFICATION:
				{
					supportVectorMachineModel = encodeClassification(pmmlKernel, sv, nSv, rho, coefs, schema);
				}
				break;
			case ONE_CLASSIFICATION:
				{
					Transformation outlier = new OutlierTransformation(){

						@Override
						public Expression createExpression(FieldRef fieldRef){
							return PMMLUtil.createApply(PMMLFunctions.LESSOREQUAL, fieldRef, PMMLUtil.createConstant(0d));
						}
					};

					supportVectorMachineModel = encodeRegression(pmmlKernel, sv, rho, coefs, schema)
						.setOutput(ModelUtil.createPredictedOutput(FieldName.create("decisionFunction"), OpType.CONTINUOUS, DataType.DOUBLE, outlier));

					if(yScale != null && yScale.size() > 0){
						throw new IllegalArgumentException();
					}
				}
				break;
			case EPS_REGRESSION:
			case NU_REGRESSION:
				{
					supportVectorMachineModel = encodeRegression(pmmlKernel, sv, rho, coefs, schema);

					if(yScale != null && yScale.size() > 0){
						RDoubleVector yScaledCenter = yScale.getDoubleElement("scaled:center");
						RDoubleVector yScaledScale = yScale.getDoubleElement("scaled:scale");

						supportVectorMachineModel.setTargets(ModelUtil.createRescaleTargets(-1d * yScaledScale.asScalar(), yScaledCenter.asScalar(), (ContinuousLabel)schema.getLabel()));
					}
				}
				break;
			default:
				throw new IllegalArgumentException();
		}

		return supportVectorMachineModel;
	}

	private void encodeFormula(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector type = svm.getDoubleElement("type");
		RDoubleVector sv = svm.getDoubleElement("SV");
		RVector<?> levels = svm.getVectorElement("levels");
		RExp terms = svm.getElement("terms");
		RGenericVector xlevels = DecorationUtil.getGenericElement(svm, "xlevels");

		Type svmType = Type.values()[ValueUtil.asInt(type.asScalar())];

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		FormulaContext context = new XLevelsFormulaContext(xlevels);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		switch(svmType){
			case C_CLASSIFICATION:
			case NU_CLASSIFICATION:
				FormulaUtil.setLabel(formula, terms, levels, encoder);
				break;
			case ONE_CLASSIFICATION:
				encoder.setLabel(new ContinuousLabel(null, DataType.DOUBLE));
				break;
			case EPS_REGRESSION:
			case NU_REGRESSION:
				FormulaUtil.setLabel(formula, terms, null, encoder);
				break;
		}

		FormulaUtil.addFeatures(formula, columnNames, true, encoder);

		scaleFeatures(encoder);
	}

	private void encodeNonFormula(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector type = svm.getDoubleElement("type");
		RDoubleVector sv = svm.getDoubleElement("SV");
		RVector<?> levels = svm.getVectorElement("levels");

		Type svmType = Type.values()[ValueUtil.asInt(type.asScalar())];

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

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

		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			DataField dataField = encoder.createDataField(FieldName.create(columnName), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.addFeature(dataField);
		}

		scaleFeatures(encoder);
	}

	private void scaleFeatures(RExpEncoder encoder){
		RGenericVector svm = getObject();

		RDoubleVector sv = svm.getDoubleElement("SV");
		RBooleanVector scaled = svm.getBooleanElement("scaled");
		RGenericVector xScale = svm.getGenericElement("x.scale");

		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		List<Feature> features = encoder.getFeatures();

		if((scaled.size() != columnNames.size()) || (scaled.size() != features.size())){
			throw new IllegalArgumentException();
		}

		RDoubleVector xScaledCenter = xScale.getDoubleElement("scaled:center");
		RDoubleVector xScaledScale = xScale.getDoubleElement("scaled:scale");

		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			if(!scaled.getValue(i)){
				continue;
			}

			Feature feature = features.get(i);

			Double center = xScaledCenter.getElement(columnName);
			Double scale = xScaledScale.getElement(columnName);

			if(ValueUtil.isZero(center) && ValueUtil.isOne(scale)){
				continue;
			}

			ContinuousFeature continuousFeature = feature.toContinuousFeature();

			Expression expression = continuousFeature.ref();

			if(!ValueUtil.isZero(center)){
				expression = PMMLUtil.createApply(PMMLFunctions.SUBTRACT, expression, PMMLUtil.createConstant(center));
			} // End if

			if(!ValueUtil.isOne(scale)){
				expression = PMMLUtil.createApply(PMMLFunctions.DIVIDE, expression, PMMLUtil.createConstant(scale));
			}

			DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("scale", continuousFeature), OpType.CONTINUOUS, DataType.DOUBLE, expression);

			features.set(i, new ContinuousFeature(encoder, derivedField));
		}
	}

	static
	private SupportVectorMachineModel encodeClassification(org.dmg.pmml.support_vector_machine.Kernel kernel, RDoubleVector sv, RIntegerVector nSv, RDoubleVector rho, RDoubleVector coefs, Schema schema){
		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		return LibSVMUtil.createClassification(kernel, new FortranMatrix<>(sv.getValues(), rowNames.size(), columnNames.size()), nSv.getValues(), rowNames.getValues(), rho.getValues(), Lists.transform(coefs.getValues(), SVMConverter.FUNCTION_NEGATE), schema);
	}

	static
	private SupportVectorMachineModel encodeRegression(org.dmg.pmml.support_vector_machine.Kernel kernel, RDoubleVector sv, RDoubleVector rho, RDoubleVector coefs, Schema schema){
		RStringVector rowNames = sv.dimnames(0);
		RStringVector columnNames = sv.dimnames(1);

		return LibSVMUtil.createRegression(kernel, new FortranMatrix<>(sv.getValues(), rowNames.size(), columnNames.size()), rowNames.getValues(), rho.asScalar(), Lists.transform(coefs.getValues(), SVMConverter.FUNCTION_NEGATE), schema);
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
