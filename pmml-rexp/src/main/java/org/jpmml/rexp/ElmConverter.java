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

import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.neural_network.NeuralEntity;
import org.dmg.pmml.neural_network.NeuralInputs;
import org.dmg.pmml.neural_network.NeuralLayer;
import org.dmg.pmml.neural_network.NeuralNetwork;
import org.dmg.pmml.neural_network.NeuralOutputs;
import org.dmg.pmml.neural_network.Neuron;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.neural_network.NeuralNetworkUtil;

public class ElmConverter extends ModelConverter<RGenericVector> {

	public ElmConverter(RGenericVector elm){
		super(elm);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector elm = getObject();

		RGenericVector model = DecorationUtil.getGenericElement(elm, "model");

		RExp terms = model.getAttribute("terms");

		RStringVector columns = terms.getStringAttribute("columns");

		FormulaContext context = new ModelFrameFormulaContext(model);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		FormulaUtil.setLabel(formula, terms, null, encoder);

		List<String> names = FormulaUtil.removeSpecialSymbol(columns.getValues(), "(Intercept)", 0);

		FormulaUtil.addFeatures(formula, names, true, encoder);
	}

	@Override
	public NeuralNetwork encodeModel(Schema schema){
		RGenericVector elm = getObject();

		RDoubleVector inpweight = elm.getDoubleElement("inpweight");
		RDoubleVector biashid = elm.getDoubleElement("biashid");
		RDoubleVector outweight = elm.getDoubleElement("outweight");
		RStringVector actfun = elm.getStringElement("actfun");
		RDoubleVector nhid = elm.getDoubleElement("nhid");

		Label label = schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		switch(actfun.asScalar()){
			case "purelin":
				break;
			default:
				throw new IllegalArgumentException();
		}

		NeuralInputs neuralInputs = NeuralNetworkUtil.createNeuralInputs(features, DataType.DOUBLE);

		List<? extends NeuralEntity> entities = neuralInputs.getNeuralInputs();

		List<NeuralLayer> neuralLayers = new ArrayList<>(2);

		NeuralLayer hiddenNeuralLayer = new NeuralLayer();

		int rows = ValueUtil.asInt(nhid.asScalar());
		int columns = features.size();

		for(int row = 0; row < rows; row++){
			List<Double> weights = FortranMatrixUtil.getRow(inpweight.getValues(), rows, columns, row);
			Double bias = (!biashid.isEmpty() ? biashid.getValue(row) : null);

			Neuron neuron = NeuralNetworkUtil.createNeuron(entities, weights, bias)
				.setId("hidden/" + String.valueOf(row + 1));

			hiddenNeuralLayer.addNeurons(neuron);
		}

		neuralLayers.add(hiddenNeuralLayer);

		entities = hiddenNeuralLayer.getNeurons();

		NeuralLayer outputNeuralLayer = new NeuralLayer();

		// XXX
		columns = 1;

		for(int column = 0; column < columns; column++){
			List<Double> weights = FortranMatrixUtil.getColumn(outweight.getValues(), rows, columns, column);
			Double bias = null;

			Neuron neuron = NeuralNetworkUtil.createNeuron(entities, weights, bias)
				.setId("output/" + String.valueOf(column + 1));

			outputNeuralLayer.addNeurons(neuron);
		}

		neuralLayers.add(outputNeuralLayer);

		entities = outputNeuralLayer.getNeurons();

		NeuralOutputs neuralOutputs = NeuralNetworkUtil.createRegressionNeuralOutputs(entities, (ContinuousLabel)label);

		NeuralNetwork neuralNetwork = new NeuralNetwork(MiningFunction.REGRESSION, NeuralNetwork.ActivationFunction.IDENTITY, ModelUtil.createMiningSchema(label), neuralInputs, neuralLayers)
			.setNeuralOutputs(neuralOutputs);

		return neuralNetwork;
	}
}