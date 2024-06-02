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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.google.common.io.ByteStreams;

public class RExpParser {

	private RDataInput input = null;

	private List<RExp> referenceTable = new ArrayList<>();


	public RExpParser(InputStream is) throws IOException {
		this.input = new XDRInput(init(new PushbackInputStream(is, 2)));
	}

	public RExp parse() throws IOException {
		int version = readInt();
		if(version != 2){
			throw new IllegalArgumentException(String.valueOf(version));
		}

		int writerVersion = readInt();
		int releaseVersion = readInt();

		RExp result = readRExp();

		try {
			readInt();

			throw new IllegalStateException();
		} catch(EOFException eofe){
			// Ignored
		}

		return result;
	}

	private RExp readRExp() throws IOException {
		int flags = readInt();

		int type = SerializationUtil.decodeType(flags);
		switch(type){
			case SExpTypes.SYMSXP:
				return readSymbol();
			case SExpTypes.LISTSXP:
				return readPairList(flags);
			case SExpTypes.CLOSXP:
				return readClosure(flags);
			case SExpTypes.ENVSXP:
				return readEnvironment(flags);
			case SExpTypes.PROMSXP:
				return readPromise(flags);
			case SExpTypes.LANGSXP:
				return readFunctionCall(flags);
			case SExpTypes.CHARSXP:
				return readString(flags);
			case SExpTypes.LGLSXP:
				return readLogicalVector(flags);
			case SExpTypes.INTSXP:
				return readIntVector(flags);
			case SExpTypes.REALSXP:
				return readRealVector(flags);
			case SExpTypes.STRSXP:
				return readStringVector(flags);
			case SExpTypes.DOTSXP:
				return readEllipsis(flags);
			case SExpTypes.VECSXP:
			case SExpTypes.EXPRSXP:
				return readVector(flags);
			case SExpTypes.BCODESXP:
				return readBytecode(flags);
			case SExpTypes.EXTPTRSXP:
				return readExternalPointer(flags);
			case SExpTypes.RAWSXP:
				return readRaw(flags);
			case SExpTypes.S4SXP:
				return readS4Object(flags);
			case SerializationTypes.BASEENVSXP:
				return null; // XXX
			case SerializationTypes.EMPTYENVSXP:
				return null; // XXX
			case SerializationTypes.NAMESPACESXP:
				return readNamespace();
			case SerializationTypes.BASENAMESPACESXP:
				return null; // XXX
			case SerializationTypes.MISSINGARGSXP:
				return null; // XXX
			case SerializationTypes.UNBOUNDVALUESXP:
				return null; // XXX
			case SerializationTypes.GLOBALENVSXP:
				return null; // XXX
			case SerializationTypes.NILVALUESXP:
				return null;
			case SerializationTypes.REFSXP:
				return readReference(flags);
			default:
				throw new UnsupportedOperationException(String.valueOf(type));
		}
	}

	private RString readSymbol() throws IOException {
		RString symbol = (RString)readRExp();

		if(symbol.getValue() == null){
			symbol = RString.NA;
		}

		this.referenceTable.add(symbol);

		return symbol;
	}

	private RPair readPairList(int flags) throws IOException {
		RPair first = null;
		RPair last = null;

		while(true){
			int type = SerializationUtil.decodeType(flags);

			if(type == SerializationTypes.NILVALUESXP){
				break;
			}

			RPair attributes = readAttributes(flags);

			RExp tag = readTag(flags);
			RExp value = readRExp();

			RPair pair = new RPair(tag, value, attributes);

			if(first == null){
				first = pair;
				last = pair;
			} else

			{
				last.setNext(pair);
				last = pair;
			}

			flags = readInt();
		}

		return first;
	}

	private RExp readClosure(int flags) throws IOException {
		RPair attributes = readAttributes(flags);

		RExp environment = readTag(flags);
		RPair parameters = (RPair)readRExp();
		RExp body = readRExp();

		return new RClosure(attributes, environment, parameters, body);
	}

	private RExp readEnvironment(int flags) throws IOException {
		RExp rexp = null;

		readInt();

		// "MUST register before filling in"
		this.referenceTable.add(rexp);

		// "Now fill it in"
		RExp parent = readRExp();
		RPair frame = (RPair)readRExp();
		RExp hashtab = readRExp();

		RPair attributes = (RPair)readRExp();

		return rexp;
	}

	private RExp readPromise(int flags) throws IOException {
		RPair attributes = readAttributes(flags);

		RExp environment = readTag(flags);
		RExp value = readRExp();
		RExp expression = readRExp();

		return null;
	}

	private RFunctionCall readFunctionCall(int flags) throws IOException {
		RPair attributes = readAttributes(flags);

		RExp tag = readTag(flags);
		RExp function = readRExp();
		RPair arguments = (RPair)readRExp();

		return new RFunctionCall(tag, function, arguments, attributes);
	}

	private RString readString(int flags) throws IOException {
		int length = readInt();
		if(length == -1){
			return new RString(null);
		}

		byte[] buffer = readByteArray(length);

		String value;

		if(SerializationUtil.isBytesCharset(flags)){
			value = new String(buffer, StandardCharsets.US_ASCII);
		} else

		if(SerializationUtil.isLatin1Charset(flags)){
			value = new String(buffer, StandardCharsets.ISO_8859_1);
		} else

		if(SerializationUtil.isUTF8Charset(flags)){
			value = new String(buffer, StandardCharsets.UTF_8);
		} else

		{
			value = new String(buffer);
		}

		return new RString(value);
	}

	private RBooleanVector readLogicalVector(int flags) throws IOException {
		int length = readInt();

		int[] values = new int[length];

		for(int i = 0; i < length; i++){
			int value = readInt();

			values[i] = value;
		}

		return new RBooleanVector(values, readAttributes(flags));
	}

	private RIntegerVector readIntVector(int flags) throws IOException {
		int length = readInt();

		int[] values = new int[length];

		for(int i = 0; i < length; i++){
			int value = readInt();

			values[i] = value;
		}

		RIntegerVector result = new RIntegerVector(values, readAttributes(flags));
		if(result.hasAttribute("levels")){
			result = new RFactorVector(values, result.getAttributes());
		}

		return result;
	}

	private RDoubleVector readRealVector(int flags) throws IOException {
		int length = readInt();

		double[] values = new double[length];

		for(int i = 0; i < length; i++){
			double value = readDouble();

			values[i] = value;
		}

		return new RDoubleVector(values, readAttributes(flags));
	}

	private RStringVector readStringVector(int flags) throws IOException {
		int length = readInt();

		List<String> values = new ArrayList<>(length);

		for(int i = 0; i < length; i++){
			RString string = (RString)readRExp();

			values.add(string.getValue());
		}

		return new RStringVector(values, readAttributes(flags));
	}

	private RExp readEllipsis(int flags) throws IOException {
		RPair attributes = readAttributes(flags);

		RExp environment = readTag(flags);
		RExp value = readRExp();
		RExp expression = readRExp();

		return null;
	}

	private RGenericVector readVector(int flags) throws IOException {
		int length = readInt();

		List<RExp> values = new ArrayList<>(length);

		for(int i = 0; i < length; i++){
			RExp rexp = readRExp();

			values.add(rexp);
		}

		return new RGenericVector(values, readAttributes(flags));
	}

	private RExp readBytecode(int flags) throws IOException {
		int length = readInt();

		RExp[] reps = new RExp[length];

		return readBC1(reps);
	}

	private RExp readExternalPointer(int flags) throws IOException {
		RExp rexp = null;

		this.referenceTable.add(rexp);

		RExp protected_ = readRExp();
		RExp tag = readRExp();

		readAttributes(flags);

		return rexp;
	}

	private RRaw readRaw(int flags) throws IOException {
		int length = readInt();

		byte[] value = readByteArray(length);

		return new RRaw(value, readAttributes(flags));
	}

	private RExp readBC1(RExp[] reps) throws IOException {
		RExp code = readRExp();

		RExp[] constants = readBCConsts(reps);

		return constants[0];
	}

	private RExp[] readBCConsts(RExp[] reps) throws IOException {
		int n = readInt();

		RExp[] pool = new RExp[n];

		for(int i = 0; i < n; i++){
			int type = readInt();

			switch(type){
				case SExpTypes.LISTSXP:
				case SExpTypes.LANGSXP:
					pool[i] = readBCLang(type, reps);
					break;
				case SExpTypes.BCODESXP:
					pool[i] = readBC1(reps);
					break;
				case SerializationTypes.ATTRLISTSXP:
				case SerializationTypes.ATTRLANGSXP:
				case SerializationTypes.BCREPREF:
				case SerializationTypes.BCREPDEF:
					pool[i] = readBCLang(type, reps);
					break;
				default:
					pool[i] = readRExp();
					break;
			}
		}

		return pool;
	}

	private RExp readBCLang(int type, RExp[] reps) throws IOException {

		switch(type){
			case SExpTypes.LISTSXP:
			case SExpTypes.LANGSXP:
			case SerializationTypes.ATTRLISTSXP:
			case SerializationTypes.ATTRLANGSXP:
			case SerializationTypes.BCREPDEF:
				int pos = -1;
				if(type == SerializationTypes.BCREPDEF){
					pos = readInt();
					type = readInt();
				}

				RPair attributes;

				switch(type){
					case SerializationTypes.ATTRLISTSXP:
					case SerializationTypes.ATTRLANGSXP:
						attributes = readAttributes();
						break;
					default:
						attributes = null;
						break;
				}

				RPair pair;

				switch(type){
					case SExpTypes.LISTSXP:
					case SerializationTypes.ATTRLISTSXP:
						pair = new RPair(null, null, attributes);
						break;
					case SExpTypes.LANGSXP:
					case SerializationTypes.ATTRLANGSXP:
						pair = new RFunctionCall(null, null, null, attributes);
						break;
					default:
						throw new UnsupportedOperationException(String.valueOf(type));
				}

				if(pos >= 0){
					reps[pos] = pair;
				}

				RExp tag = readRExp();
				pair.setTag(tag);

				RExp value = readBCLang(readInt(), reps);
				pair.setValue(value);

				RPair next = (RPair)readBCLang(readInt(), reps);
				if(next != null){
					pair.setNext(next);
				}

				return pair;
			case SerializationTypes.BCREPREF:
				return reps[readInt()];
			default:
				return readRExp();
		}
	}

	private S4Object readS4Object(int flags) throws IOException {
		return new S4Object(readAttributes(flags));
	}

	private RExp readNamespace() throws IOException {
		int flags = readInt();
		if(flags != 0){
			throw new UnsupportedOperationException();
		}

		readStringVector(flags);

		this.referenceTable.add(null);

		return null;
	}

	private RExp readReference(int flags) throws IOException {
		int refIndex = SerializationUtil.unpackRefIndex(flags);

		if(refIndex == 0){
			refIndex = readInt();
		}

		return this.referenceTable.get(refIndex - 1);
	}

	private RExp readTag(int flags) throws IOException {

		if(SerializationUtil.hasTag(flags)){
			return readRExp();
		}

		return null;
	}

	private RPair readAttributes(int flags) throws IOException {

		if(SerializationUtil.hasAttributes(flags)){
			return readAttributes();
		}

		return null;
	}

	private RPair readAttributes() throws IOException {
		return (RPair)readRExp();
	}

	private int readInt() throws IOException {
		return this.input.readInt();
	}

	private double readDouble() throws IOException {
		return this.input.readDouble();
	}

	private byte[] readByteArray(int length) throws IOException {
		return this.input.readByteArray(length);
	}

	static
	private InputStream init(PushbackInputStream is) throws IOException {
		byte[] gzipMagic = new byte[2];

		ByteStreams.readFully(is, gzipMagic);

		is.unread(gzipMagic);

		if(Arrays.equals(RExpParser.GZIP_MAGIC, gzipMagic)){
			return new GZIPInputStream(is);
		}

		return is;
	}

	private static final byte[] GZIP_MAGIC = {(byte)0x1f, (byte)0x8b};
}