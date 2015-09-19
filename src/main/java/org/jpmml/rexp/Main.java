/*
 * Copyright (c) 2014 Villu Ruusmann
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.transform.stream.StreamResult;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.protobuf.CodedInputStream;
import org.dmg.pmml.PMML;
import org.jpmml.model.JAXBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

	@Parameter (
		names = "--converter",
		description = "Converter class"
	)
	private String converter = null;

	@Parameter (
		names = "--pb-input",
		description = "ProtoBuf input file",
		required = true
	)
	private File input = null;

	@Parameter (
		names = "--pmml-output",
		description = "PMML output file",
		required = true
	)
	private File output = null;


	static
	public void main(String... args) throws Exception {
		Main main = new Main();

		JCommander commander = new JCommander(main);
		commander.setProgramName(Main.class.getName());

		try {
			commander.parse(args);
		} catch(ParameterException pe){
			commander.usage();

			System.exit(-1);
		}

		main.run();
	}

	public void run() throws Exception {
		RExp rexp;

		InputStream is = new FileInputStream(this.input);

		try {
			logger.info("Parsing ProtoBuf..");

			CodedInputStream cis = CodedInputStream.newInstance(is);
			cis.setSizeLimit(Integer.MAX_VALUE);

			long start = System.currentTimeMillis();
			rexp = RExp.parseFrom(cis);
			long end = System.currentTimeMillis();

			logger.info("Parsed ProtoBuf in {} ms.", (end - start));
		} catch(Exception e){
			logger.error("Failed to parse ProtoBuf", e);

			throw e;
		} finally {
			is.close();
		}

		Converter converter;

		if(this.converter != null){
			logger.info("Initializing user-specified Converter {}", this.converter);

			Class<?> clazz = Class.forName(this.converter);

			converter = (Converter)clazz.newInstance();
		} else

		{
			logger.info("Initializing default Converter");

			ConverterFactory converterFactory = ConverterFactory.newInstance();

			converter = converterFactory.newConverter(rexp);
		}

		{
			Class<?> clazz = converter.getClass();

			logger.info("Initialized {}", clazz.getName());
		}

		PMML pmml;

		try {
			logger.info("Converting model..");

			long start = System.currentTimeMillis();
			pmml = converter.convert(rexp);
			long end = System.currentTimeMillis();

			logger.info("Converted model in {} ms.", (end - start));
		} catch(Exception e){
			logger.error("Failed to convert model", e);

			throw e;
		}

		OutputStream os = new FileOutputStream(this.output);

		try {
			logger.info("Marshalling PMML..");

			long start = System.currentTimeMillis();
			JAXBUtil.marshalPMML(pmml, new StreamResult(os));
			long end = System.currentTimeMillis();

			logger.info("Marshalled PMML in {} ms.", (end - start));
		} catch(Exception e){
			logger.error("Failed to marshal PMML", e);

			throw e;
		} finally {
			os.close();
		}
	}

	public String getConverter(){
		return this.converter;
	}

	public void setConverter(String converter){
		this.converter = converter;
	}

	public File getInput(){
		return this.input;
	}

	public void setInput(File input){

		if(input == null){
			throw new NullPointerException();
		}

		this.input = input;
	}

	public File getOutput(){
		return this.output;
	}

	public void setOutput(File output){

		if(output == null){
			throw new NullPointerException();
		}

		this.output = output;
	}

	private static final Logger logger = LoggerFactory.getLogger(Main.class);
}