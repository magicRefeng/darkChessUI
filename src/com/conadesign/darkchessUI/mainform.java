package com.conadesign.darkchessUI;
/**
 * Protocol:
 * Board describe:
 * 1. horizontal 
 * 2. so has 8 columns, from A~H
 * 3. has 4 rows,from 1~4
 * Chess describe:
 * Empty place '-', 
 * Red King 'K', 帥
 * Red Guard 'G', 仕 
 * Red 'M', 相 
 * Red Rook 'R', 俥
 * Red Knight 'N', 傌
 * Red Cannon 'C', 炮
 * Red 'P', 兵
 * Black part 'k', 'g', 'm', 'r', 'n', 'c', 'p', 
 * not flip 'X'.
 * 
 * Server                     Client
<----------(J)oin------------
---(F)irst/(S)econd/(R)eject--->  //Reject will close session
<---------(G)rant[Name max 20 char, left shift right padding]---------------
------(N)ame[Name max 20 char, left shift right padding]--------------->//opposite player name, and then waiting for server to call for Move
----------(C)all------------->    //Call for Move
<-------(O)pen[A-H][1-4]/(M)ove[A-H][1-4][A-H][1-4]---
--------(O)pen[A-H][1-4][CHESS]/(M)ove[A-H][1-4][CHESS][A-H][1-4][CHESS]---/(I)nvalid------>  //O/M will broadcast, "I" only return to calli, after I, will send "C" again 
--------Yo(U)[(R)ed/(B)lack]----------> // to tell client which color they are
---------Yo(U)[(W)in/(L)ose/(D)raw]-------->   //when one of player send U or has no more chess in board
-----------Next Game(X)------------------------->Next Game
<-----------Next Game(X) accept-------------------------Next Game ready
<---------(D)isconnect--------> //after receive D, just close session

suddenly disconnect and reconnect:
<----------Jo(I)n------------
--------Transfer Last (B)oard State---------->//傳送現在的盤面,32byte board, 16bytes dead black, 16 bytes dead red
----(T)ransfer--->//Transfer for transfer the step action from start to last, it follow the command
----------(C)all------------->    //Call for Move if waiting for this client send move

 */
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JList;
import javax.swing.ListModel;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import javax.swing.JScrollPane;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class mainform extends JFrame implements Runnable{
	/**
	 * 
	 */
	private final static int smallchessWidth=30;
	private final static int smallchessHeight=30;
	private final static int chessWidth=60;
	private final static int chessHeight=60;
	private static final long serialVersionUID = 1L;
	private JComponent canvas;
	private BufferedImage offScreen;
	Graphics graphics,backgraphics;
	JList<String> listContent;
	JList<String> listFile;
	JLabel lblPlayer1;
	JLabel lblPlayer2;
	private JTextField edtWinMatches;
	private JTextField edtTotalMatches;
	private JTextField edtListen;
	private JTextField edtTimeout;
	private JTextField edtFreestep;
	JButton btnStart,btnExport;
	private String CurrentFileName;
	String path = new java.io.File(".").getCanonicalPath();
	//0=空, 1=卒, 2=包, 3=馬, 4=車, 5=象, 6=士, 7=將.
	//     8=兵, 9=炮, 10=傌, 11=俥, 12=相, 13=仕, 14=帥, 15=未翻
	public int [][] board=new int[4][8];
	public int [][] darkboard=new int [4][8];
	public int [][] logdarkboard=new int [4][8];
	public int freestepCount,freestepMax,logMaxLines;
	//每次選到頻色後, 就要將SOCKET 指到ACOLOR 中
	Socket [] socket=new Socket[2];
	String [] Names={"",""};
	public int [][] score=new int[2][3];//[0][]=黑的/win/lose/draw
	public int matchMax,matchWin,curMatch;//最多幾盤, 幾盤勝, 現在為第幾盤
	public int [] atimer=new int[2];//0 = BLACK 所對應的TIMER, 1=RED 所對應的TIMER
	public int [] acolor=new int[2];//SOCKET 所對應的 COLOR
	//public int curPlayer;//0=black, 1=red, 2=no played -->由這去acolor 查到要往哪個SOCKET 去send request, 也可以查到對應的atimer
	//[0][] = 存黑子, [1][]=存紅子, 由acolor 的值,可以查到紅/黑是畫在上或下方
	public int [][] deadChess={{0,1,1,1,1,1,2,2,3,3,4,4,5,5,6,6,7},{0,8,8,8,8,8,9,9,10,10,11,11,12,12,13,13,14}};
	public int [] deadcount={16,16};
	int darkCount;
	byte [] charmapper={'-','p','c','n','r','m','g','k','P','C','N','R','M','G','K','X'};
	String [] scharmapper={"-","p","c","n","r","m","g","k","P","C","N","R","M","G","K","X"};
	HashMap<String,Integer>  invertcharmapper= new HashMap<String,Integer>();
	int fx=-1,fy,tx=-1,ty;
	public int currentplayer=2,logfirstPlayer;//0 = socket 0, 1= socket 1, 2 = not in playing
	int firstMove;
	int logindex;
	int isTimeout;
	private  Image [] imgChess = new Image[16];
	private  Image imgboard,lhand,rhand,redIcon,blackIcon ;
	private boolean isServerStart=false;
    private boolean OutServer = false;
    private ServerSocket server;
    String curPath;
	private int[] curTimer=new int[3];
	private timerThread tt;
	public JLabel lblTimer2;
	public JLabel lblTimer1;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					mainform window = new mainform();
					window.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 * @throws IOException 
	 */
	public mainform() throws IOException {
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				OutServer=true;
				try{
					if(socket[0]!=null)
						socket[0].close();
					if(socket[1]!=null)
						socket[1].close();
				}catch (Exception e){
					
				}
			}
		});
		loadImg();
		initialize();
		socket[0]=null;
		socket[1]=null;
		curMatch=0;
	}
	public void startGame(){
		suffle();
		System.out.println("Dark Board:");
    	for (int i=0;i<4;i++){
    		String q="";
    		for (int j=0;j<8;j++){
    			q+=String.format("%02d", darkboard[i][j])+",";
    		}
    		System.out.println(q);
    	}
    	System.out.println("Log Dark Board:");
    	for (int i=0;i<4;i++){
    		String q="";
    		for (int j=0;j<8;j++){
    			q+=String.format("%02d", logdarkboard[i][j])+",";
    		}
    		System.out.println(q);
    	}
    	String s="";
		for (int i=0;i<16;i++){
			s+=scharmapper[deadChess[0][i]];
		}
		System.out.println("Black chess:"+s);
		s="";
		for (int i=0;i<16;i++){
			s+=scharmapper[deadChess[1][i]];
		}
		System.out.println("Red chess:"+s);
		fx=-1;
		tx=-1;
		curMatch++;
		currentplayer=1-(curMatch%2);
		firstMove=1;
		if (isServerStart==false){
	        Thread t=new Thread (this);
	        t. start();
			isServerStart=true;
		}
		canvas.repaint();
    	curTimer[0]=atimer[0]*1000;
    	curTimer[1]=atimer[1]*1000;
    	isTimeout=-1;
	}
	public void clearBoard(){
		for (int i=0;i<4;i++){
			for (int j=0;j<8;j++){
				if (board[i][j]>0&&board[i][j]<15){
					capedChess(board[i][j]);
				}else
				if (darkboard[i][j]>0){
					capedChess(darkboard[i][j]);
				}
			}
		}
	}
	public void suffle(){
		int i,j,k;
		int bx=0,by=0;
		Random rnd2=new Random(System.currentTimeMillis());
		Random rnd=new Random(rnd2.nextLong());
		clearBoard();
		i=0;
		do{
			int color;
			int chess;
			color=rnd.nextInt(2);
			chess=rnd.nextInt(16)+1;
			j=0;
			if (deadChess[color][chess]>0){
				darkboard[by][bx]=deadChess[color][chess];
				logdarkboard[by][bx]=deadChess[color][chess];
				board[by][bx]=15;
				deadChess[color][chess]=0;
				bx++;
				if (bx==8){
					bx=0;
					by++;
				}
			}else{
				for (k=chess+1;k<17;k++){
					if (deadChess[color][k]>0){
						darkboard[by][bx]=deadChess[color][k];
						logdarkboard[by][bx]=deadChess[color][k];
						board[by][bx]=15;
						deadChess[color][k]=0;
						bx++;
						if (bx==8){
							bx=0;
							by++;
						}
						j=1;
						break;
					}
				}
				if (j==0){
					for (k=1;k<17;k++){
						if (deadChess[1-color][k]>0){
							darkboard[by][bx]=deadChess[1-color][k];
							logdarkboard[by][bx]=deadChess[1-color][k];
							board[by][bx]=15;
							deadChess[1-color][k]=0;
							bx++;
							if (bx==8){
								bx=0;
								by++;
							}
							j=1;
							break;
						}
					}
				}
				if (j==0){
					for (k=1;k<chess;k++){
						if (deadChess[color][k]>0){
							darkboard[by][bx]=deadChess[color][k];
							logdarkboard[by][bx]=deadChess[color][k];
							board[by][bx]=15;
							deadChess[color][k]=0;
							bx++;
							if (bx==8){
								bx=0;
								by++;
							}
							j=1;
							break;
						}
					}
				}
				if (j==0){
					//WHAT'S HAPPEN?
				}
			}
			i++;
		}while(i<32);
		deadcount[0]=0;
		deadcount[1]=0;
		darkCount=32;
	}
	public void capedChess(int c){
		if (c>7){
			deadcount[1]++;
			if (deadcount[1]==17){
				dumpboard();
			}
			deadChess[1][deadcount[1]]=c;
		}else
		if (c>0){
			deadcount[0]++;
			if (deadcount[0]==17){
				dumpboard();
			}
			deadChess[0][deadcount[0]]=c;
		}
	}
	// 1= currentplayer win, 2 =draw game, 0 = nothing happen, 3 = currentplay lose
	//** this function must be call before change side **
	public int checkState(){
		int opponent=1-currentplayer;
		System.out.println("Check current color:"+acolor[currentplayer]+",op color:"+acolor[opponent]);
		if (deadcount[acolor[opponent]]==16){
			System.out.println("all dead of color:"+opponent);
			dumpboard();
			return 1;
		}
		System.out.println("darkCount:"+darkCount);
		if (darkCount==0&&hasMove(acolor[opponent])==0){
			System.out.println("color:"+acolor[opponent]+" has no moves" );
			return 1;
		}
		System.out.println("still has move of side:"+acolor[opponent]);
		if (curTimer[currentplayer]<=0){
			return 3;
		}
		if (freestepCount>freestepMax){
			return 2;
		}
		return 0;
	}
	private void dumpboard() {
		System.out.println("start dump board...");
    	for (int i=0;i<4;i++){
    		String q="";
    		for (int j=0;j<8;j++){
    			q+=String.format("%02d", board[i][j])+",";
    		}
    		System.out.println(q);
    	}
		System.out.println("start dump darkboard...");
    	for (int i=0;i<4;i++){
    		String q="";
    		for (int j=0;j<8;j++){
    			q+=String.format("%02d", darkboard[i][j])+",";
    		}
    		System.out.println(q);
    	}
    	System.out.println("start dump logdarkboard...");
    	for (int i=0;i<4;i++){
    		String q="";
    		for (int j=0;j<8;j++){
    			q+=String.format("%02d", logdarkboard[i][j])+",";
    		}
    		System.out.println(q);
    	}
    	System.out.println("start dump deadChess...");
    	for (int i=0;i<2;i++){
    		String q="";
    		for (int j=1;j<17;j++){
    			q+=String.format("%02d", deadChess[i][j])+",";
    		}
    		System.out.println(q);
    	}
	}
	int checkCannonCap(int x,int y,int oppolower, int oppoupper){
		int i,j,result=0;;
		/* X axis positive direction */
		j=8;
		for (i=x+1;i<7;i++)
			if (board[y][i]!=0){
				j=i;
				break;
			}
		for (i=j+1;i<8;i++)
			if (board[y][i]>oppolower&&board[y][i]<oppoupper){
				result++;
				break;
			}else
			if (board[y][i]!=0)
				break;
		/* X axis negative direction */
		j=-1;
		for (i=x-1;i>0;i--)
			if (board[y][i]!=0){
				j=i;
				break;
			}
		for (i=j-1;i>-1;i--)
			if (board[y][i]>oppolower&&board[y][i]<oppoupper){
				result++;
				break;
			}else
			if (board[y][i]!=0)
				break;
		/* Y axis positive direction */
		j=4;
		for (i=y+1;i<3;i++)
			if (board[i][x]!=0){
				j=i;
				break;
			}
		for (i=j+1;i<4;i++)
			if (board[i][x]>oppolower&&board[i][x]<oppoupper){
				result++;
				break;
			}else
			if (board[i][x]!=0)
				break;
		/* Y axis negative direction */
		j=-1;
		for (i=y-1;i>0;i--)
			if (board[i][x]!=0){
				j=i;
				break;
			}
		for (i=j-1;i>-1;i--)
			if (board[i][x]>oppolower&&board[i][x]<oppoupper){
				result++;
				break;
			}else
			if (board[i][x]!=0)
				break;
		return result;
	}
	private int hasMove(int icolor) {
		System.out.println("checking color hasMove of :"+icolor);
		for (int i=0;i<4;i++){
			for (int j=0;j<8;j++){
				if (icolor==0){
					if (board[i][j]>0&&board[i][j]<8){
						System.out.println("black chess at i="+i+",j="+j+" is :"+board[i][j]);
						if (board[i][j]==2){
							if (checkCannonCap(j,i,7,15)>0){
								System.out.println("checkCannonCap 7-15 at i="+i+",j="+j);
								return 1;
							}
							if (hasWay(i,j)>0){
								System.out.println("hasway at i="+i+",j="+j);
								return 1;
							}
						}else{
							if (hasWay(i,j)>0){
								System.out.println("hasway at i="+i+",j="+j);
								return 1;
							}
							if (hasCap(i,j)>0){
								System.out.println("hasCap at i="+i+",j="+j);
								return 1;
							}
						}
					}
				}else{
					if (board[i][j]>7&&board[i][j]<15){
						System.out.println("red chess at i="+i+",j="+j+" is :"+board[i][j]);
						if (board[i][j]==9){
							if (checkCannonCap(j,i,0,8)>0){
								System.out.println("checkCannonCap 0-8 at i="+i+",j="+j);
								return 1;
							}
							if (hasWay(i,j)>0){
								System.out.println("hasway at i="+i+",j="+j);
								return 1;
							}
						}else{
							if (hasWay(i,j)>0){
								System.out.println("hasway at i="+i+",j="+j);
								return 1;
							}
							if (hasCap(i,j)>0){
								System.out.println("hasCap at i="+i+",j="+j);
								return 1;
							}
						}
					}
				}
			}
		}
		return 0;
	}
	private int hasCap(int y, int x) {
		if (x==0&&y==0){
			if (canCap(board[y][x],board[y+1][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x+1])>0){
				return 1;
			}
			return 0;
		}
		if (x==0&&y==3){
			if (canCap(board[y][x],board[y-1][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x+1])>0){
				return 1;
			}
			return 0;
		}
		if (x==7&&y==0){
			if (canCap(board[y][x],board[y+1][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x-1])>0){
				return 1;
			}
			return 0;	
		}
		if (x==7&&y==3){
			if (canCap(board[y][x],board[y-1][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x-1])>0){
				return 1;
			}
			return 0;
		}
		if (x==0){
			if (canCap(board[y][x],board[y+1][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y-1][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x+1])>0){
				return 1;
			}
			return 0;
		}
		if (x==7){
			if (canCap(board[y][x],board[y-1][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y+2][x])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x-1])>0){
				return 1;
			}
			return 0;
		}
		if (y==0){
			if (canCap(board[y][x],board[y][x-1])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x+1])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y+1][x])>0){
				return 1;
			}
			return 0;
		}
		if (y==3){
			if (canCap(board[y][x],board[y][x+1])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y][x-1])>0){
				return 1;
			}
			if (canCap(board[y][x],board[y-1][x])>0){
				return 1;
			}
			return 0;
		}
		if (canCap(board[y][x],board[y+1][x])>0){
			return 1;
		}
		if (canCap(board[y][x],board[y-1][x])>0){
			return 1;
		}
		if (canCap(board[y][x],board[y][x+1])>0){
			return 1;
		}
		if (canCap(board[y][x],board[y][x-1])>0){
			return 1;
		}
		return 0;
	}


	private int hasWay(int y, int x) {
		if (x==0&&y==0){
			if (board[y+1][x]==0){
				return 1;
			}
			if (board[y][x+1]==0){
				return 1;
			}
			return 0;
		}
		if (x==0&&y==3){
			if (board[y-1][x]==0){
				return 1;
			}
			if (board[y][x+1]==0){
				return 1;
			}
			return 0;
		}
		if (x==7&&y==0){
			if (board[y+1][x]==0){
				return 1;
			}
			if (board[y][x-1]==0){
				return 1;
			}
			return 0;
		}
		if (x==7&&y==3){
			if (board[y-1][x]==0){
				return 1;
			}
			if (board[y][x-1]==0){
				return 1;
			}
			return 0;
		}
		if (x==0){
			if (board[y+1][x]==0){
				return 1;
			}
			if (board[y][x+1]==0){
				return 1;
			}
			if (board[y-1][x]==0){
				return 1;
			}
			return 0;
		}
		if (x==7){
			if (board[y-1][x]==0){
				return 1;
			}
			if (board[y+1][x]==0){
				return 1;
			}
			if (board[y][x-1]==0){
				return 1;
			}
			return 0;
		}
		if (y==0){
			if (board[y+1][x]==0){
				return 1;
			}
			if (board[y][x-1]==0){
				return 1;
			}
			if (board[y][x+1]==0){
				return 1;
			}
			return 0;
		}
		if (y==3){
			if (board[y-1][x]==0){
				return 1;
			}
			if (board[y][x+1]==0){
				return 1;
			}
			if (board[y][x-1]==0){
				return 1;
			}
			return 0;
		}
		if (board[y+1][x]==0){
			return 1;
		}
		if (board[y-1][x]==0){
			return 1;
		}
		if (board[y][x+1]==0){
			return 1;
		}
		if (board[y][x-1]==0){
			return 1;
		}
		return 0;
	}

	public int canCap(int i,int j){
		if (i==15||j==15)
			return 0;
		if (i==0||j==0)
			return 0;
		if (i>7&&j>7){
			return 0;
		}
		if (i<8&&j<8){
			return 0;
		}
		if (i==2||i==9)
			return 1;
		if (i==1&&j==14){
			return 1;
		}
		if (i==8&&j==7){
			return 1;
		}
		if (i==14&&j==1){
			return 0;
		}
		if (i==7&&j==8){
			return 0;
		}		
		if (i<8){
			if (i+7>=j)
				return 1;
		}else{
			if (i>=j+7)
				return 1;
		}
		return 0;
	}
	private void drawboard(Graphics g){
		int i,j,k=1,l;
		backgraphics.drawImage(imgboard, 0, 0, canvas.getWidth(), canvas.getHeight(), canvas);
		for (i=0;i<2;i++){
			l=30;
			for (j=1;j<17;j++){
				if (deadChess[i][j]>0){
					backgraphics.drawImage(imgChess[deadChess[i][j]], l, k,smallchessWidth,smallchessHeight,canvas);
				}else{
					break;
				}
				l+=smallchessWidth+1;
			}
			k+=smallchessHeight-1;
		}
		k=72;
		for (i=0;i<4;i++){
			l=52;
			for (j=0;j<8;j++){
				if (board[i][j]>0){
					backgraphics.drawImage(imgChess[board[i][j]], l, k,chessWidth,chessHeight, canvas);
				}
				l+=chessWidth-3;
			}
			k+=chessHeight+12;
		}
		if (acolor[0]==0){
			backgraphics.drawImage(blackIcon, 0, 4,30,40, canvas);
			backgraphics.drawImage(redIcon, canvas.getWidth()-34, 4,30,40, canvas);
		}else{
			backgraphics.drawImage(redIcon, 0, 4,30,40, canvas);
			backgraphics.drawImage(blackIcon, canvas.getWidth()-34, 4,30,40, canvas);
		}
		System.out.println("currentplayer="+currentplayer);
		if (currentplayer<2){
			if (currentplayer==1){
				backgraphics.drawImage(rhand, canvas.getWidth()-100, 20,60,80, canvas);
			}else{
				backgraphics.drawImage(lhand, 34, 20,60,80, canvas);
			}
		}
		if (fx==-1){
			if (tx>-1){
				backgraphics.setColor(Color.RED);
				backgraphics.drawRect(52+tx*(chessWidth-3), 72+ty*(chessHeight+12), chessWidth, chessHeight+4);
			}
		}else{
			backgraphics.setColor(Color.YELLOW);
			backgraphics.drawRect(52+fx*(chessWidth-3), 72+fy*(chessHeight+12), chessWidth, chessHeight+4);
			backgraphics.setColor(Color.RED);
			backgraphics.drawRect(52+tx*(chessWidth-3), 72+ty*(chessHeight+12), chessWidth, chessHeight+4);
		}
		backgraphics.drawString(""+score[1][0], canvas.getWidth()-34, 54);
		backgraphics.drawString(""+score[1][1], canvas.getWidth()-34, 74);
		backgraphics.drawString(""+score[1][2], canvas.getWidth()-34, 94);
		backgraphics.drawString(lblPlayer2.getText().trim(), canvas.getWidth()-34, 114);
		backgraphics.drawString(""+score[0][0], 4, 54);
		backgraphics.drawString(""+score[0][1], 4, 74);
		backgraphics.drawString(""+score[0][2], 4, 94);
		backgraphics.drawString(lblPlayer1.getText().trim(), 4, 114);
		backgraphics.drawString(curMatch+"/"+matchMax, 4, 300);
		backgraphics.drawString(freestepCount+"/"+freestepMax, canvas.getWidth()-34, 300);
		g.drawImage(offScreen, 0, 0,null);
	}

	private void loadImg(){
		ClassLoader cl = this.getClass().getClassLoader();
		for (int i=1;i<16;i++){
			imgChess[i]=new ImageIcon(cl.getResource("images/"+i+".png")).getImage();
		}
		imgboard = new ImageIcon(cl.getResource("images/"+"board.png")).getImage();
		redIcon = new ImageIcon(cl.getResource("images/"+"redOne.png")).getImage();
		blackIcon = new ImageIcon(cl.getResource("images/"+"blackOne.png")).getImage();
		rhand=new ImageIcon(cl.getResource("images/"+"righArrow.png")).getImage();
		lhand=new ImageIcon(cl.getResource("images/"+"leftArrow.png")).getImage();
	}

	/*
	private void loadImg(){
		for (int i=1;i<16;i++){
			imgChess[i]=new ImageIcon(path+"/images/"+i+".png").getImage();
		}
		imgboard = new ImageIcon(path+"/images/board.png").getImage();
		redIcon = new ImageIcon(path+"/images/redOne.png").getImage();
		blackIcon = new ImageIcon(path+"/images/blackOne.png").getImage();
		rhand=new ImageIcon(path+"/images/righArrow.png").getImage();
		lhand=new ImageIcon(path+"/images/leftArrow.png").getImage();
	}
*/
	/**
	 * Initialize the contents of the frame.
	 * @throws IOException 
	 */
	  public String getBoardInfo(){
			String s="";
			int i,j;
			for (i=0;i<4;i++)
			  for (j=0;j<8;j++){
			    if (board[i][j]>0&&board[i][j]<15)
			      s+=scharmapper[board[i][j]];
			    else
			    if (board[i][j]==0)
			      s+="-";
			    else
			      s+="X";
			}
			for (i=1;i<17;i++){
			  s+=scharmapper[deadChess[0][i]];
			}
			for (i=1;i<17;i++){
			  s+=scharmapper[deadChess[1][i]];
			}
			s+=currentplayer+"";
			return s;
		}
	private void initialize() throws IOException {
		currentplayer=2;
		System.out.println(path);
		setBounds(100, 100, 870, 402);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		getContentPane().setLayout(null);
		canvas = new JComponent() {
            /**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override // check this is a real method
            public void paintComponent(Graphics g) {
            	super.paintComponent(g);
            	drawboard(g);
            }
        };
        // layout mana
		canvas.setBounds(0, 0, 557, 364);
		getContentPane().add(canvas);
		setVisible(true);
		offScreen = (BufferedImage)createImage(canvas.getWidth(), canvas.getHeight());
		backgraphics = offScreen.createGraphics();
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setBounds(554, 0, 300, 364);
		getContentPane().add(tabbedPane);
		
		JPanel tabPanel1 = new JPanel();
		tabbedPane.addTab("Setting", null, tabPanel1, null);
		tabPanel1.setLayout(null);
		
		lblPlayer1 = new JLabel("Player 1");
		lblPlayer1.setBounds(10, 10, 130, 15);
		tabPanel1.add(lblPlayer1);
		
		lblPlayer2 = new JLabel("Player 2");
		lblPlayer2.setBounds(10, 35, 130, 15);
		tabPanel1.add(lblPlayer2);
		
		JLabel lblMatches = new JLabel("Matches:");
		lblMatches.setBounds(10, 80, 53, 15);
		tabPanel1.add(lblMatches);
		
		edtWinMatches = new JTextField();
		edtWinMatches.setText("6");
		edtWinMatches.setBounds(63, 77, 30, 21);
		tabPanel1.add(edtWinMatches);
		edtWinMatches.setColumns(10);
		
		edtTotalMatches = new JTextField();
		edtTotalMatches.setText("10");
		edtTotalMatches.setBounds(121, 77, 30, 21);
		tabPanel1.add(edtTotalMatches);
		edtTotalMatches.setColumns(10);
		
		JLabel label = new JLabel("/");
		label.setBounds(103, 80, 46, 15);
		tabPanel1.add(label);
		
		btnStart = new JButton("Start New Game");
		btnStart.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				freestepMax=Integer.parseInt(edtFreestep.getText());
				matchMax=Integer.parseInt(edtTotalMatches.getText());
				matchWin=Integer.parseInt(edtWinMatches.getText());
				curMatch=0;
				deadChess[0][0 ]=0;
				deadChess[0][1 ]=1;
				deadChess[0][2 ]=1;
				deadChess[0][3 ]=1;
				deadChess[0][4 ]=1;
				deadChess[0][5 ]=1;
				deadChess[0][6 ]=2;
				deadChess[0][7 ]=2;
				deadChess[0][8 ]=3;
				deadChess[0][9 ]=3;
				deadChess[0][10]=4;
				deadChess[0][11]=4;
				deadChess[0][12]=5;
				deadChess[0][13]=5;
				deadChess[0][14]=6;
				deadChess[0][15]=6;
				deadChess[0][16]=7;
				deadChess[1][0 ]=0;
				deadChess[1][1 ]=8;
				deadChess[1][2 ]=8;
				deadChess[1][3 ]=8;
				deadChess[1][4 ]=8;
				deadChess[1][5 ]=8;
				deadChess[1][6 ]=9;
				deadChess[1][7 ]=9;
				deadChess[1][8 ]=10;
				deadChess[1][9 ]=10;
				deadChess[1][10]=11;
				deadChess[1][11]=11;
				deadChess[1][12]=12;
				deadChess[1][13]=12;
				deadChess[1][14]=13;
				deadChess[1][15]=13;
				deadChess[1][16]=14;
				deadcount[0]=16;
				deadcount[1]=16;
				atimer[0]=Integer.parseInt(edtTimeout.getText());
				atimer[1]=atimer[0];
				startGame();
				btnStart.setEnabled(false);
			}
		});
		btnStart.setBounds(6, 302, 87, 23);
		tabPanel1.add(btnStart);
		
		JLabel lblListen = new JLabel("Listen:");
		lblListen.setBounds(10, 110, 46, 15);
		tabPanel1.add(lblListen);
		
		edtListen = new JTextField();
		edtListen.setText("29888");
		edtListen.setBounds(90, 108, 61, 21);
		tabPanel1.add(edtListen);
		edtListen.setColumns(10);
		
		JLabel lblTimeout = new JLabel("TimeOut:");
		lblTimeout.setBounds(10, 142, 68, 15);
		tabPanel1.add(lblTimeout);
		
		edtTimeout = new JTextField();
		edtTimeout.setText("1200");
		edtTimeout.setBounds(88, 139, 61, 21);
		tabPanel1.add(edtTimeout);
		edtTimeout.setColumns(10);
		
		JLabel lblSeconds = new JLabel("Seconds");
		lblSeconds.setBounds(153, 142, 66, 15);
		tabPanel1.add(lblSeconds);
		
		JLabel lblFreeStep = new JLabel("Free Step:");
		lblFreeStep.setBounds(8, 169, 61, 15);
		tabPanel1.add(lblFreeStep);
		
		edtFreestep = new JTextField();
		edtFreestep.setText("40");
		edtFreestep.setBounds(113, 166, 38, 21);
		tabPanel1.add(edtFreestep);
		edtFreestep.setColumns(10);
		
		JLabel lblDrawGme = new JLabel("draw game");
		lblDrawGme.setBounds(155, 170, 64, 15);
		tabPanel1.add(lblDrawGme);
		
		lblTimer1 = new JLabel("00:00");
		lblTimer1.setBounds(150, 10, 69, 15);
		tabPanel1.add(lblTimer1);
		
		lblTimer2 = new JLabel("00:00");
		lblTimer2.setBounds(150, 35, 69, 15);
		tabPanel1.add(lblTimer2);
		
		JPanel tabPanel2 = new JPanel();
		tabbedPane.addTab("Logging", null, tabPanel2, null);
		tabPanel2.setLayout(null);
		
		DefaultListModel<String> model1 = new DefaultListModel<String>();
		DefaultListModel<String> model2 = new DefaultListModel<String>();
		listContent = new JList<String>(model1);
		listContent.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
			     logindex = listContent.locationToIndex(e.getPoint());
			     listContent.ensureIndexIsVisible(logindex);
			     forwardTO(logindex);
			     canvas.repaint();
			}
		});
		listContent.addKeyListener(new KeyListener(){

			@Override
			public void keyPressed(KeyEvent arg0) {
				if (arg0.getKeyCode()==KeyEvent.VK_UP){
					logindex--;
					if (logindex<0){
						logindex=0;
					}
				    forwardTO(logindex);
				    canvas.repaint();
				}else
				if (arg0.getKeyCode()==KeyEvent.VK_DOWN){
					logindex++;
					if (logMaxLines<logindex){
						logindex=logMaxLines;
					}
				    forwardTO(logindex);
				    canvas.repaint();
				}
				
			}

			@Override
			public void keyReleased(KeyEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void keyTyped(KeyEvent arg0) {
				// TODO Auto-generated method stub
				
			}
		});
		JScrollPane scrollPane1 = new JScrollPane(listContent);
		scrollPane1.setBounds(4, 25, 280, 190);
		tabPanel2.add(scrollPane1);
		
		JLabel lblFile = new JLabel("File:");
		lblFile.setBounds(8, 224, 46, 15);
		tabPanel2.add(lblFile);
		
		btnExport = new JButton("to Clipboard");
		btnExport.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				Toolkit toolkit = Toolkit.getDefaultToolkit();
				Clipboard clipboard = toolkit.getSystemClipboard();
				StringSelection strSel = new StringSelection(getBoardInfo());
				clipboard.setContents(strSel, null);
			}
		});
		btnExport.setBounds(140, 217, 127, 20);
		tabPanel2.add(btnExport);
		
		JButton btnFirst = new JButton("|<<");
		btnFirst.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				cleanToBack();
				logindex=0;
				currentplayer=logfirstPlayer;
				listContent.setSelectedIndex(0);
				listContent.ensureIndexIsVisible(listContent.getSelectedIndex());
				canvas.repaint();
			}
		});
		btnFirst.setBounds(0, 0, 55, 23);
		tabPanel2.add(btnFirst);
		JButton btnBack = new JButton("<");
		btnBack.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				listContent.setSelectedIndex(logindex);
				listContent.ensureIndexIsVisible(listContent.getSelectedIndex());
				logindex--;
				if (logindex<0){
					logindex=0;
				}
			    forwardTO(logindex);
			    canvas.repaint();
			}
		});
		btnBack.setBounds(56, 0, 55, 23);
		tabPanel2.add(btnBack);
		JButton btnForward = new JButton(">");
		btnForward.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				logindex++;
				if (logMaxLines<logindex){
					logindex=logMaxLines;
				}
				listContent.setSelectedIndex(logindex);
				listContent.ensureIndexIsVisible(listContent.getSelectedIndex());
			    forwardTO(logindex);
			    canvas.repaint();
			}
		});
		btnForward.setBounds(112, 0, 55, 23);
		tabPanel2.add(btnForward);
		
		JButton btnLast = new JButton(">>|");
		btnLast.setBounds(168, 0, 55, 23);
		btnLast.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				forwardTO(logMaxLines);
		        logindex=logMaxLines;
				listContent.setSelectedIndex(logindex-1);
				listContent.ensureIndexIsVisible(listContent.getSelectedIndex());
				canvas.repaint();
			}
		});
		tabPanel2.add(btnLast);
		
		JButton btnSave = new JButton("Save");
		btnSave.setBounds(224, 0, 60, 23);
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					exportList( (DefaultListModel<String>) listContent.getModel(),199);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		tabPanel2.add(btnSave);
		

		

		curPath=new java.io.File(".").getCanonicalPath();
		listFile = new JList<String>(model2);
		listFile.addMouseListener(new ActionJListFile(listFile));
		JScrollPane scrollPane = new JScrollPane(listFile);
		scrollPane.setBounds(4, 237, 280, 94);
		tabPanel2.add(scrollPane);
	    File folder = new File(curPath);
	    File[] listOfFiles = folder.listFiles();
	    Arrays.sort(listOfFiles, new Comparator<File>(){
	        public int compare(File f1, File f2)
	        {
	            return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
	        } });
	    for (int i = 0; i < listOfFiles.length; i++) {
	      if (listOfFiles[i].isFile()) {
	    	  if (listOfFiles[i].getName().indexOf(".txt")>-1||listOfFiles[i].getName().indexOf(".TXT")>-1){
	    		  ( (DefaultListModel<String>) listFile.getModel() ).addElement(listOfFiles[i].getName());
	    	  }
	      }/* else if (listOfFiles[i].isDirectory()) {
	    	  ( (DefaultListModel<String>) listFile.getModel() ).addElement(listOfFiles[i].getName()+"/");
	      }*/
	    }
	    invertcharmapper.put("-", 0);
	    invertcharmapper.put("p", 1);
	    invertcharmapper.put("c", 2);
	    invertcharmapper.put("n", 3);
	    invertcharmapper.put("r", 4);
	    invertcharmapper.put("m", 5);
	    invertcharmapper.put("g", 6);
	    invertcharmapper.put("k", 7);
	    invertcharmapper.put("P", 8);
	    invertcharmapper.put("C", 9);
	    invertcharmapper.put("N", 10);
	    invertcharmapper.put("R", 11);
	    invertcharmapper.put("M", 12);
	    invertcharmapper.put("G", 13);
	    invertcharmapper.put("K", 14);
	    invertcharmapper.put("X", 15);
	}
	private void cleanToBack() {
	   	 for (int i=0;i<2;i++){
	   		 for (int j=0;j<17;j++){
	   			 deadChess[i][j]=0;
	   		 }
	   	 }
	   	 for (int i=0;i<4;i++){
	   		 for (int j=0;j<8;j++){
	   			 board[i][j]=15;
	   		 }
	   	 }
		deadcount[0]=0;
		deadcount[1]=0;
		copyboard();
	}
	public class ActionJListFile extends MouseAdapter{
		  protected JList<String> list;
		
		    
		  public ActionJListFile(JList<String> l){
		   list = l;
		  }

		  public void mouseClicked(MouseEvent e){
		   if(curMatch==0&&e.getClickCount() == 2){
			     int index = list.locationToIndex(e.getPoint());
			     ListModel<String> dlm = list.getModel();
			     String item = dlm.getElementAt(index);
			     list.ensureIndexIsVisible(index);
			     System.out.println("Double clicked on " + item);
			     try {
			    	cleanToBack();
			    	CurrentFileName=item;
					importList(item);
					canvas.repaint();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
		     }
		   }

		}
	@Override
	public void run() {//main loop
        try {
            server = new ServerSocket(Integer.parseInt(edtListen.getText()));
        } catch (java.io.IOException e) {
            System.out.println("Socket啟動有問題 !");
            System.out.println("IOException :" + e.toString());
        }
        DataInputStream [] in=new DataInputStream[2];
        DataOutputStream [] out=new DataOutputStream[2];
    	byte [] buf = new byte[4096];
    	byte [] outbuf = new byte[4096];
        int hands=0;
        int gameState=0;
        long stime,etime;
        OutServer=false;
        System.out.println("伺服器已啟動 !");
        try {
        	while (!OutServer) {
            	if (hands<2){
            		System.out.println("waiting for login...."+hands);
	                socket[hands] = server.accept();
	                in[hands] = new DataInputStream (socket[hands].getInputStream());
	                out[hands] = new DataOutputStream (socket[hands].getOutputStream());
		            // Get input from the client
		            in[hands].read(buf,0,1);
		            if (buf[0]=='J'){
		            	if (hands==0){
		            		outbuf[0]='F';
		            	}else{
		            		outbuf[0]='S';
		            	}
		            	out[hands].write(outbuf,0,1);
		            	in[hands].read(buf,0,1);
		            	if (buf[0]=='G'){
		            		in[hands].read(buf,0,20);
		            		if (hands==0){
		            			Names[0]=new String(buf);
		            			lblPlayer1.setText(Names[0]);
		            		}else{
		            			Names[1]=new String(buf);
		            			lblPlayer2.setText(Names[1]);
		            			out[hands].write(('N'+Names[0]).getBytes());
		            			out[hands].flush();
		            			out[hands-1].write(('N'+Names[1]).getBytes());
		    		            out[hands-1].flush();
		    		            currentplayer=0;
		            		}
		            	}else{
		                	outbuf[0]='R';
		                	out[hands].write(outbuf,0,1);
		                	out[hands].flush();
		            	}
		            }else{
		            	outbuf[0]='R';
		            	out[hands].write(outbuf,0,1);
		            	out[hands].flush();
		            }
		            hands++;
            	}
            	if (hands==2){//after two player login
            		if (tt==null){
            			tt=new timerThread();
            			tt.start();
            		}
            		do{
	            		String s=null;
	            		System.out.println("send request....");
	            		for (int i=0;i<40;i++)
	            			buf[i]=0;
	            		do{
	            			stime=System.currentTimeMillis();
		        			outbuf[0]='C';
		        			out[currentplayer].write(outbuf,0,1);
		        			out[currentplayer].flush();
		        			in[currentplayer].read(buf,0,1);
		        			if (buf[0]=='O'){
		        				in[currentplayer].read(buf,1,2);
		        			}else
		        			if (buf[0]=='M'){
		        				in[currentplayer].read(buf,1,4);
		        			}
		        			System.out.println("get "+new String(buf).trim()+" from client:"+currentplayer+" its color is "+acolor[currentplayer]);
		    				s=doClientRequest(buf);
		    				if (s!=null){
		    					if (firstMove==2){
		    						if (s.charAt(3)>'a'){
		    							acolor[currentplayer]=0;
		    							acolor[1-currentplayer]=1;
		    							System.out.println(currentplayer+":get BLACK");
		    						}else{
		    							acolor[currentplayer]=1;
		    							acolor[1-currentplayer]=0;
		    							System.out.println(currentplayer+":get RED");
		    						}
		    						if (acolor[currentplayer]==1){
				    					out[currentplayer].write("UR".getBytes());
				    					out[1-currentplayer].write("UB".getBytes());
		    						}else{
				    					out[currentplayer].write("UB".getBytes());
				    					out[1-currentplayer].write("UR".getBytes());
		    						}
		    						firstMove=0;
		    					}
		    					( (DefaultListModel<String>) listContent.getModel() ).addElement(s);
		    					out[0].write(s.getBytes());
		    					out[1].write(s.getBytes());
		    					out[0].flush();
		    					out[1].flush();
		    					System.out.println("write "+s+" to clients");
		    				}else{
		    					( (DefaultListModel<String>) listContent.getModel() ).addElement(Names[currentplayer]+" send wrong Move request:"+new String(buf));
		    					dumpboard();
			        			outbuf[0]='I';
			        			out[currentplayer].write(outbuf,0,1);
			        			out[currentplayer].flush();
		    				}
		    				if (isTimeout>-1){
		    					break;
		    				}
	            		}while (s==null);
	            		if ((gameState=checkState())>0){
	            			System.out.println("Game:"+curMatch+" finish.");
	            			int nextGame=0;
	            			if (gameState==1){//current player win
		            			outbuf[0]='U';
		            			outbuf[1]='W';
		    					out[currentplayer].write(outbuf,0,2);
		    					out[currentplayer].flush();
		            			outbuf[0]='U';
		            			outbuf[1]='L';
		    					out[1-currentplayer].write(outbuf,0,2);
		    					out[1-currentplayer].flush();
		    					score[currentplayer][0]++;
		    					score[1-currentplayer][1]++;
	            			}else
	            			if (gameState==2){//draw game
	            				System.out.println("draw game:"+currentplayer+" color is "+acolor[currentplayer]);
		            			outbuf[0]='U';
		            			outbuf[1]='D';
		    					out[currentplayer].write(outbuf,0,2);
		    					out[currentplayer].flush();
		    					out[1-currentplayer].write(outbuf,0,2);
		    					out[1-currentplayer].flush();
		    					score[currentplayer][2]++;
		    					score[1-currentplayer][2]++;
	            			}else
	            			if (gameState==3){//lose game
	            				System.out.println("lose game:"+currentplayer+" color is "+acolor[currentplayer]);
		            			outbuf[0]='U';
		            			outbuf[1]='L';
		    					out[currentplayer].write(outbuf,0,2);
		    					out[currentplayer].flush();
		            			outbuf[0]='U';
		            			outbuf[1]='W';
		    					out[1-currentplayer].write(outbuf,0,2);
		    					out[1-currentplayer].flush();
		    					score[currentplayer][1]++;
		    					score[1-currentplayer][0]++;
	            			}
	            			exportList( (DefaultListModel<String>) listContent.getModel(),gameState);
	    					if (score[currentplayer][0]==matchWin){
	    						curMatch=matchMax+1;
	    					}
	            			if (curMatch+1<=matchMax){
		            			outbuf[0]='X';
		    					out[0].write(outbuf,0,1);
		    					in[0].read(buf, 0, 1);
		    					if (buf[0]=='X'){
		    						out[1].write(outbuf,0,1);
		    						in[1].read(buf, 0, 1);
		    						if (buf[0]=='X'){
		    							nextGame=1;
		    						}
		    					}
		    					if (nextGame==0)
		    						break;
		            			startGame();
	            			}else{
	            				curMatch++;
	            			}
	            		}else{
		        			etime=System.currentTimeMillis();
		        			stime=etime-stime;
		        			long r=(stime%1000);
		        			if (r>0){
		        				curTimer[currentplayer]-=r;
		        				curTimer[1-currentplayer]+=r;
		        			}
	            			currentplayer=1-currentplayer;//change request side
	            		}
	            		canvas.repaint();
            		}while (curMatch<=matchMax);
            		OutServer=true;
        			outbuf[0]='D';
					out[0].write(outbuf,0,1);
					out[1].write(outbuf,0,1);
            	}
            }//end while
        } catch (Exception e) {
            System.out.println("Socket連線有問題 !");
            System.out.println("Exception :" + e.toString());
            e.printStackTrace();
        }finally{
    		currentplayer=2;
    		btnStart.setEnabled(true);
      	  try {
      		socket[0].close();
      		socket[1].close();
      	  } catch (IOException e) {
  			// TODO Auto-generated catch block
  			e.printStackTrace();
      	  }
        }        
	}
	public  void exportList(ListModel<String> model,int rtype) throws IOException{
		File f=null;
		String filename;
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		Date today = Calendar.getInstance().getTime();
		String datestr = dateFormat.format(today);
		
		if (rtype==1){
			if (currentplayer==0){
				filename="R"+curMatch+"_"+Names[0].trim()+"(WIN)_vs_"+Names[1].trim()+"_"+datestr+".txt";
			}else{
				filename="R"+curMatch+"_"+Names[0].trim()+"_vs_"+Names[1].trim()+"(WIN)_"+datestr+".txt";
			}
		}else
		if (rtype==3){
			if (currentplayer==0){
				filename="R"+curMatch+"_"+Names[1].trim()+"(WIN)_vs_"+Names[0].trim()+"_"+datestr+".txt";
			}else{
				filename="R"+curMatch+"_"+Names[1].trim()+"_vs_"+Names[0].trim()+"(WIN)_"+datestr+".txt";
			}
		}else
		if (rtype!=199){
			filename="R"+curMatch+"(Draw)_"+Names[0].trim()+"_vs_"+Names[1].trim()+"_"+datestr+".txt";
		}else{
			filename="R"+curMatch+"(Save)_"+Names[0].trim()+"_vs_"+Names[1].trim()+"_"+datestr+".txt";
		}
		f=new File(path+"/"+filename);
		System.out.println("export to "+filename);
		//f=new File("D:\\image\\"+filename);
	    PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
	    try {
	    	for (int i=0;i<4;i++){
	    		String q="";
	    		for (int j=0;j<8;j++){
	    			q+=String.format("%02d", logdarkboard[i][j])+",";
	    		}
	    		pw.println(q );
	    	}
	    	String q=score[0][0]+"/"+score[0][1]+"/"+score[0][2]+","+lblTimer1.getText()+";"+lblTimer2.getText();
	    	pw.println(q );
	        int len = model.getSize();
	        for(int i = 0; i < len; i++) {
	           pw.println(((DefaultListModel<String>) model).get(i).toString());
	        }
	        if (rtype!=199)
	        	( (DefaultListModel<String>) listContent.getModel() ).clear();
	    }finally {
	        pw.close();
	    }
	}
	public void copyboard(){
		for (int i=0;i<4;i++){
			for (int j=0;j<8;j++){
				darkboard[i][j]=logdarkboard[i][j];
			}
		}
	}
	public void importList(String aFile) throws IOException{
	    BufferedReader input =  new BufferedReader(new FileReader(curPath+"/"+aFile));
	    byte [] b=aFile.getBytes();
	    String [] fnames= aFile.split("_");
	    System.out.println("fnames:"+fnames.length);
	    String p1Name=fnames[1];
	    String p2Name=fnames[3];
	    lblPlayer1.setText(p1Name);
	    lblPlayer2.setText(p2Name);
	    ( (DefaultListModel<String>) listContent.getModel() ).clear();
        String line = null;
        int lineCount=0;
        darkCount=32;
        while (( line = input.readLine()) != null){
        	if (lineCount<4){
        		String [] p=line.split(",");
        		for (int i=0;i<8;i++){
        			logdarkboard[lineCount][i]=Integer.parseInt(p[i]);
        			darkboard[lineCount][i]=logdarkboard[lineCount][i];
        		}
        		for (int i=0;i<2;i++){
        			for (int j=0;j<17;j++){
        				deadChess[i][j]=0;
        			}
        		}
        		deadcount[0]=0;
        		deadcount[1]=0;
        	}else
        	if (lineCount>4){
            	( (DefaultListModel<String>) listContent.getModel() ).addElement(String.format("%03d", (lineCount-4))+". "+line);
            	//doLogActionForward(line);
            	if (lineCount==5){
            		for (int z=0;z<b.length;z++){
            			System.out.println("b[z]="+(char)b[z]);
            			if (b[z]=='_'||b[z]=='('){
            				String ss=new String(b,1,z-1);
            				System.out.println("SS="+ss);
            				System.out.println("lineCount="+lineCount);
            				int iround=Integer.parseInt(ss);
            				byte [] bb=line.getBytes();
            				if (iround%2==1){
            					currentplayer=1;
            					if (bb[3]>'a'){
	    							acolor[1]=1;
	    							acolor[0]=0;
            					}else{
	    							acolor[1]=0;
	    							acolor[0]=1;
            					}
            				}else{
            					currentplayer=1;
            					if (bb[3]>'a'){
	    							acolor[1]=0;
	    							acolor[0]=1;
            					}else{
	    							acolor[1]=1;
	    							acolor[0]=0;
            					}
            				}
            				logfirstPlayer=currentplayer;
            				break;
            			}
            			
            		}
            	}
            	currentplayer=1-currentplayer;
        	}else{
        		String [] p1=line.split(",");
        		String [] p=p1[0].split("/");
        		score[0][0]=Integer.parseInt(p[0]);
        		score[0][1]=Integer.parseInt(p[1]);
        		score[0][2]=Integer.parseInt(p[2]);
        		score[1][0]=score[0][1];
        		score[1][1]=score[0][0];
        		score[1][2]=score[0][2];
        		if (p1.length>1){
        			if (!p1[1].equals("")){
        				String [] p2=p1[1].split(";");
        				lblTimer1.setText(p2[0]);
        				lblTimer2.setText(p2[1]);
        			}
        		}
        	}
        	lineCount++;
        }
        logindex=lineCount-5;
        logMaxLines=logindex;
        forwardTO(logMaxLines);
        System.out.println("import currentplayer="+currentplayer);
        System.out.println("import logMaxLines="+logMaxLines);
        System.out.println("1 - currentplayer has move:"+hasMove(1-currentplayer));
        System.out.println("currentplayer has move:"+hasMove(currentplayer));
        input.close();
	}
	private void doLogActionForward(String s){
		byte [] buf=s.getBytes();
		if (buf[0]=='O'){
			int x = buf[1]-'A';
			int y = buf[2]-'1';
			board[y][x]=invertcharmapper.get(new String(buf,3,1));
			darkboard[y][x]=0;
			fx=-1;
			tx=x;
			ty=y;
		}else
		if (buf[0]=='M'){
		    int x1=buf[1]-'A';
		    int y1=buf[2]-'1';
		    int x2=buf[4]-'A';
		    int y2=buf[5]-'1';
		    board[y1][x1]=invertcharmapper.get(new String(buf,3,1));
		    board[y2][x2]=invertcharmapper.get(new String(buf,6,1));
		    if (board[y2][x2]>0){
		    	capedChess(board[y2][x2]);
		    }
		    board[y2][x2]=board[y1][x1];
		    board[y1][x1]=0;
			fx=x1;
			fy=y1;
			tx=x2;
			ty=y2;
		}
	}
	private void forwardTO(int index){
		cleanToBack();
        for(int i = 0; i < index; i++) {
        	String line=((DefaultListModel<String>) listContent.getModel()).get(i).toString();
        	if (line.indexOf(".")>-1){
        		//System.out.println(line);
        		line=line.substring(line.indexOf(".")+2,line.length());
        		//System.out.println(line);
        	}
        	doLogActionForward(line);
        	if (i==0){
        		byte b[]=CurrentFileName.getBytes();
        		for (int z=0;z<b.length;z++){
        			if (b[z]=='_'||b[z]=='('){
        				String ss=new String(b,1,z-1);
        				System.out.println("SS="+ss);
        				System.out.println("lineCount="+i);
        				int iround=Integer.parseInt(ss);
        				byte [] bb=line.getBytes();
        				if (iround%2==1){
        					currentplayer=1;
        					if (bb[3]>'a'){
    							acolor[1]=1;
    							acolor[0]=0;
        					}else{
    							acolor[1]=0;
    							acolor[0]=1;
        					}
        				}else{
        					currentplayer=1;
        					if (bb[3]>'a'){
    							acolor[1]=0;
    							acolor[0]=1;
        					}else{
    							acolor[1]=1;
    							acolor[0]=0;
        					}
        				}
        				break;
        			}
        		}
        	}
        	System.out.print(i);
        	checkState();
        	currentplayer=1-currentplayer;
	    }
	}
	private String doClientRequest(byte[] buf) {
		if (buf[0]=='O'){
			int x = buf[1]-'A';
			int y = buf[2]-'1';
			if (x>-1&&x<8){
				if (y>-1&&y<4){
					if (darkboard[y][x]>0){
						board[y][x]=darkboard[y][x];
						fx=-1;
						tx=x;
						ty=y;
						darkboard[y][x]=0;
						buf[3]=charmapper[board[y][x]];
						if (firstMove==1){
							if (buf[3]>7){
								acolor[currentplayer]=1;
								acolor[1-currentplayer]=0;
							}else{
								acolor[currentplayer]=0;
								acolor[1-currentplayer]=1;
							}
							firstMove=2;
						}
						freestepCount=0;
						darkCount--;
						return new String(buf).trim();
					}
				}
			}
		}else
		if (buf[0]=='M'){
		    int x1=buf[1]-'A';
		    int y1=buf[2]-'1';
		    int x2=buf[3]-'A';
		    int y2=buf[4]-'1';
		    int blocker=0,capture=0;
			if ((x1>-1&&x1<8)&&(y1>-1&&y1<4)&&(x2>-1&&x2<8)&&(y2>-1&&y2<4)){
				if (board[y1][x1]==2||board[y1][x1]==9){//do cannon action
					if (x1==x2){
						if (Math.abs(y2-y1)==1){
							if (board[y2][x2]==0){
								buf[5]=buf[4];
								buf[4]=buf[3];
								buf[3]=charmapper[board[y1][x1]];
								buf[6]=charmapper[board[y2][x2]];
								board[y2][x2]=board[y1][x1];
								board[y1][x1]=0;
								freestepCount++;
								fx=x1;
								fy=y1;
								tx=x2;
								ty=y2;
								return new String(buf).trim();
							}
						}else
						if (Math.abs(y2-y1)<4){//Y axis checking
							if (y2>y1){//to down side
								for (int i=y1+1;i<3;i++){
									if (board[i][x1]>0){
										blocker=i;
										break;
									}
								}
								if (blocker>0){
									for (int i=blocker+1;i<4;i++){
										if (board[i][x1]>0){
											if (canCap(board[y1][x1],board[i][x1])>0){
												capture=i;
											}
											break;
										}
									}
								}
							}else{
								for (int i=y1-1;i>1;i--){
									if (board[i][x1]>0){
										blocker=i;
										break;
									}
								}
								if (blocker>0){
									for (int i=blocker-1;i>0;i--){
										if (board[i][x1]>0){
											if (canCap(board[y1][x1],board[i][x1])>0){
												capture=i;
											}
											break;
										}
									}
								}
							}
							if (capture==y2){
								buf[5]=buf[4];
								buf[4]=buf[3];
								buf[3]=charmapper[board[y1][x1]];
								buf[6]=charmapper[board[capture][x2]];
								capedChess(board[y2][x2]);
								board[y2][x2]=board[y1][x1];
								board[y1][x1]=0;
								freestepCount=0;
								fx=x1;
								fy=y1;
								tx=x2;
								ty=y2;
								return new String(buf).trim();
							}
						}
					}else
					if (y1==y2){
						if (Math.abs(x2-x1)==1){
							if (board[y2][x2]==0){
								buf[5]=buf[4];
								buf[4]=buf[3];
								buf[3]=charmapper[board[y1][x1]];
								buf[6]=charmapper[board[y2][x2]];
								board[y2][x2]=board[y1][x1];
								board[y1][x1]=0;
								freestepCount++;
								fx=x1;
								fy=y1;
								tx=x2;
								ty=y2;
								return new String(buf).trim();
							}
						}else
						if (Math.abs(x2-x1)<8){//X axis checking
							if (x2>x1){//to right side
								for (int i=x1+1;i<7;i++){
									if (board[y1][i]>0){
										blocker=i;
										break;
									}
								}
								if (blocker>0){
									for (int i=blocker+1;i<8;i++){
										if (board[y1][i]>0){
											if (canCap(board[y1][x1],board[y1][i])>0){
												capture=i;
											}
											break;
										}
									}
								}
							}else{
								for (int i=x1-1;i>1;i--){
									if (board[y1][i]>0){
										blocker=i;
										break;
									}
								}
								if (blocker>0){
									for (int i=blocker-1;i>0;i--){
										if (board[y1][i]>0){
											if (canCap(board[y1][x1],board[y1][i])>0){
												capture=i;
											}
											break;
										}
									}
								}
							}
							if (capture==x2){
								buf[5]=buf[4];
								buf[4]=buf[3];
								buf[3]=charmapper[board[y1][x1]];
								buf[6]=charmapper[board[y1][capture]];
								capedChess(board[y2][x2]);
								board[y2][x2]=board[y1][x1];
								board[y1][x1]=0;
								freestepCount=0;
								fx=x1;
								fy=y1;
								tx=x2;
								ty=y2;
								return new String(buf).trim();
							}
						}
					}
				}else//do others chess action
				if ((Math.abs(x2-x1)==1&&Math.abs(y2-y1)==0)||(Math.abs(x2-x1)==0&&Math.abs(y2-y1)==1)){
					if (board[y2][x2]==0){
						buf[5]=buf[4];
						buf[4]=buf[3];
						buf[3]=charmapper[board[y1][x1]];
						buf[6]=charmapper[board[y2][x2]];
						board[y2][x2]=board[y1][x1];
						board[y1][x1]=0;
						freestepCount++;
						fx=x1;
						fy=y1;
						tx=x2;
						ty=y2;
						return new String(buf).trim();
					}else
					if (canCap(board[y1][x1],board[y2][x2])>0){
						buf[5]=buf[4];
						buf[4]=buf[3];
						buf[3]=charmapper[board[y1][x1]];
						buf[6]=charmapper[board[y2][x2]];
						capedChess(board[y2][x2]);
						board[y2][x2]=board[y1][x1];
						board[y1][x1]=0;
						freestepCount=0;
						fx=x1;
						fy=y1;
						tx=x2;
						ty=y2;
						return new String(buf).trim();

					}
				}
			}
				
		}
		return null;
	}
	public class timerThread extends Thread{
		public timerThread(){
			
		}
		public void run(){
			String s="";
			int [] stimer=new int[2];
			try {
				while(!OutServer){
					sleep(1000);
					curTimer[currentplayer]-=1000;
					stimer[0]= curTimer[0]/1000;
					stimer[1]= curTimer[1]/1000;
					s=String.format("%02d", stimer[0]/60)+":"+String.format("%02d", stimer[0]%60);
					lblTimer1.setText(s);
					s=String.format("%02d", stimer[1]/60)+":"+String.format("%02d", stimer[1]%60);
					lblTimer2.setText(s);
					if (curTimer[currentplayer]<=0){
						isTimeout=currentplayer;
					}
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
