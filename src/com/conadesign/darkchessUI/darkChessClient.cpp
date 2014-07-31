#include "BitBoard.h"
#include <stdlib.h>
#include <stdio.h>

#include <time.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib,"ws2_32.lib")

#define IDC_EDIT_IN		101
#define IDC_EDIT_OUT		102
#define IDC_MAIN_BUTTON		103
#define WM_SOCKET		104
/*
棋子表示  CHESS define
black  red
p       P 卒/兵
c       C 包/砲
n       N 馬/傌
r       R 車/硨
m       M 象/相
g       G 士/仕
k       K 將/帥
- 表示空格
X 表示未翻棋

棋盤表示
  A B C D E F G H
1
2
3
4
*/

//Set program Name here 
char *myProgramName="0123456789ABCDEFGHIJ";// **MUST**left shift right padding max 20 bytes
int nPort=5555;

int chessMap[15];// a mapping for set CHESS to your definition
int board[4][8];
HWND hEditIn=NULL;
HWND hEditIP=NULL;
HWND hEditPort=NULL;
SOCKET Socket=NULL;

//Text buffer length
char szHistory[2*1024*1024];
int historyLength=0;
BitBoard *BB = new BitBoard(); //AI Object new in here

HGDIOBJ hfDefault=GetStockObject(DEFAULT_GUI_FONT);
int myColor=0;

LRESULT CALLBACK WinProc(HWND hWnd,UINT message,WPARAM wParam,LPARAM lParam);

int WINAPI WinMain(HINSTANCE hInst,HINSTANCE hPrevInst,LPSTR lpCmdLine,int nShowCmd)
{
	WNDCLASSEX wClass;
	ZeroMemory(&wClass,sizeof(WNDCLASSEX));
	wClass.cbClsExtra=NULL;
	wClass.cbSize=sizeof(WNDCLASSEX);
	wClass.cbWndExtra=NULL;
	wClass.hbrBackground=(HBRUSH)COLOR_WINDOW;
	wClass.hCursor=LoadCursor(NULL,IDC_ARROW);
	wClass.hIcon=NULL;
	wClass.hIconSm=NULL;
	wClass.hInstance=hInst;
	wClass.lpfnWndProc=(WNDPROC)WinProc;
	wClass.lpszClassName="Window Class";
	wClass.lpszMenuName=NULL;
	wClass.style=CS_HREDRAW|CS_VREDRAW;

	if(!RegisterClassEx(&wClass))
	{
		int nResult=GetLastError();
		MessageBox(NULL,
			"Window class creation failed\r\nError code:",
			"Window Class Failed",
			MB_ICONERROR);
	}

	HWND hWnd=CreateWindowEx(NULL,
			"Window Class",
			"Windows Async Client",
			WS_OVERLAPPEDWINDOW,
			200,
			200,
			640,
			480,
			NULL,
			NULL,
			hInst,
			NULL);

	if(!hWnd)
	{
		int nResult=GetLastError();

		MessageBox(NULL,
			"Window creation failed\r\nError code:",
			"Window Creation Failed",
			MB_ICONERROR);
	}

    ShowWindow(hWnd,nShowCmd);

	MSG msg;
	ZeroMemory(&msg,sizeof(MSG));

	while(GetMessage(&msg,NULL,0,0))
	{
		TranslateMessage(&msg);
		DispatchMessage(&msg);
	}

	return 0;
}
void ShowMessagetoText(char *c){
	sprintf(&szHistory[historyLength],"%s\r\n",c);
	historyLength=strlen(szHistory);
	SendMessage(hEditIn,WM_SETTEXT,NULL,reinterpret_cast<LPARAM>(&szHistory));
	SendMessage(hEditIn, LOWORD(WM_VSCROLL), SB_BOTTOM, 0);
}
int ptrans(int x,int y ) { 
	return 31-(y+x*4);
} 
int iptransy(int p ) { 
	return (31-p)%4;
}
int iptransx(int p ) { 
	return (31-p)/4;
}
LRESULT CALLBACK WinProc(HWND hWnd,UINT msg,WPARAM wParam,LPARAM lParam)
{
	int nError;
	char msgBuf[80];
	char outbuf[10];
	char c;
	char   szText[256]; 
	char szIncoming[40];
	switch(msg)
	{
		case WM_CREATE:
		{
			ZeroMemory(szHistory,sizeof(szHistory));
			//set Font
			SendMessage(hEditIn,
				WM_SETFONT,
				(WPARAM)hfDefault,
				MAKELPARAM(FALSE,0));

			// Create  message box
			hEditIn=CreateWindowEx(WS_EX_CLIENTEDGE,
				"EDIT",
				"",
				WS_CHILD|WS_VISIBLE|ES_MULTILINE|
				ES_AUTOVSCROLL|ES_AUTOHSCROLL,
				50,
				120,
				400,
				200,
				hWnd,
				(HMENU)IDC_EDIT_IN,
				GetModuleHandle(NULL),
				NULL);
			if(!hEditIn)
			{
				MessageBox(hWnd,
					"Could not create incoming edit box.",
					"Error",
					MB_OK|MB_ICONERROR);
			}

			// Create IP BOX
			hEditIP=CreateWindowEx(WS_EX_CLIENTEDGE,
						"EDIT",
						"",
						WS_CHILD|WS_VISIBLE|ES_MULTILINE|
						ES_AUTOVSCROLL|ES_AUTOHSCROLL,
						50,
						50,
						300,
						20,
						hWnd,
						(HMENU)IDC_EDIT_IN,
						GetModuleHandle(NULL),
						NULL);
			if(!hEditIP)
			{
				MessageBox(hWnd,
					"Could not create outgoing edit box.",
					"Error",
					MB_OK|MB_ICONERROR);
			}

			SendMessage(hEditIP,
				WM_SETFONT,(WPARAM)hfDefault,
				MAKELPARAM(FALSE,0));
			SendMessage(hEditIP,
				WM_SETTEXT,
				NULL,
				(LPARAM)"127.0.0.1");
			// Create port BOX
			hEditPort=CreateWindowEx(WS_EX_CLIENTEDGE,
						"EDIT",
						"",
						WS_CHILD|WS_VISIBLE|ES_MULTILINE|
						ES_AUTOVSCROLL|ES_AUTOHSCROLL,
						380,
						50,
						60,
						20,
						hWnd,
						(HMENU)IDC_EDIT_IN,
						GetModuleHandle(NULL),
						NULL);
			if(!hEditPort)
			{
				MessageBox(hWnd,
					"Could not create Port edit box.",
					"Error",
					MB_OK|MB_ICONERROR);
			}

			SendMessage(hEditPort,
				WM_SETFONT,(WPARAM)hfDefault,
				MAKELPARAM(FALSE,0));
			SendMessage(hEditPort,
				WM_SETTEXT,
				NULL,
				(LPARAM)"29888");
			// Create a push button
			HWND hWndButton=CreateWindow( 
					    "BUTTON",
						"Connect",
						WS_TABSTOP|WS_VISIBLE|
						WS_CHILD|BS_DEFPUSHBUTTON,
						50,	
						330,
						75,
						23,
						hWnd,
						(HMENU)IDC_MAIN_BUTTON,
						GetModuleHandle(NULL),
						NULL);
			
			SendMessage(hWndButton,
				WM_SETFONT,
				(WPARAM)hfDefault,
				MAKELPARAM(FALSE,0));

			// Set up Winsock
			WSADATA WsaDat;
			int nResult=WSAStartup(MAKEWORD(2,2),&WsaDat);
			if(nResult!=0)
			{
				MessageBox(hWnd,
					"Winsock initialization failed",
					"Critical Error",
					MB_ICONERROR);
				SendMessage(hWnd,WM_DESTROY,NULL,NULL);
				break;
			}

			Socket=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
			if(Socket==INVALID_SOCKET)
			{
				MessageBox(hWnd,
					"Socket creation failed",
					"Critical Error",
					MB_ICONERROR);
				SendMessage(hWnd,WM_DESTROY,NULL,NULL);
				break;
			}
		}
		break;

		case WM_COMMAND:
			switch(LOWORD(wParam)){
				case IDC_MAIN_BUTTON:{
					ShowMessagetoText("Attempting to connect to server...");
					//SendMessage(hEditIn,WM_SETTEXT,NULL, (LPARAM)"Attempting to connect to server...");
					WPARAM   wParam   =   sizeof(szText);
					LPARAM   lParam   =   (LPARAM)szText;
					memset   (szText,   0,   sizeof(szText));

					SendMessage(hEditPort,   WM_GETTEXT,   wParam,   lParam); 
					nPort=atoi(szText);
					memset   (szText,   0,   sizeof(szText));
					SendMessage(hEditIP,   WM_GETTEXT,   wParam,   lParam); 
					
					// Resolve IP address for hostname
					struct hostent *host;
					if((host=gethostbyname(szText))==NULL)
					{
						MessageBox(hWnd,
							"Unable to resolve host name",
							"Critical Error",
							MB_ICONERROR);
						SendMessage(hWnd,WM_DESTROY,NULL,NULL);
						break;
					}

					// Set up our socket address structure
					SOCKADDR_IN SockAddr;
					SockAddr.sin_port=htons(nPort);
					SockAddr.sin_family=AF_INET;
					SockAddr.sin_addr.s_addr=*((unsigned long*)host->h_addr);

					connect(Socket,(LPSOCKADDR)(&SockAddr),sizeof(SockAddr));
					ShowMessagetoText("connected to server...");
					char c='J';
					send(Socket,&c,1,0);
					int nResult=WSAAsyncSelect(Socket,hWnd,WM_SOCKET,(FD_CLOSE|FD_READ));
					if(nResult)
					{
						MessageBox(hWnd,
							"WSAAsyncSelect failed",
							"Critical Error",
							MB_ICONERROR);
						SendMessage(hWnd,WM_DESTROY,NULL,NULL);
						break;
					}
				}
				break;
			}
			break;

		case WM_DESTROY:
		{
			PostQuitMessage(0);
			closesocket(Socket);
			WSACleanup();
			return 0;
		}
		break;

		case WM_SOCKET:
		{
			if(WSAGETSELECTERROR(lParam))
			{	
				MessageBox(hWnd,
					"Connection to server failed",
					"Error",
					MB_OK|MB_ICONERROR);
				SendMessage(hWnd,WM_DESTROY,NULL,NULL);
				break;
			}
			WSAAsyncSelect(Socket,hWnd,0,0);
			switch(WSAGETSELECTEVENT(lParam))
			{
				case FD_READ:
				{
					memset(szIncoming,0,sizeof(szIncoming));
					ZeroMemory(msgBuf,sizeof(msgBuf));
					int inDataLength=recv(Socket,&szIncoming[0],1,0);
					if (inDataLength==-1){
						nError=WSAGetLastError();
						sprintf(msgBuf,"%d",nError);
					  MessageBox(hWnd,
						  "connection Error1:",
						  msgBuf,
						  MB_ICONINFORMATION|MB_OK);
							closesocket(Socket);
							SendMessage(hWnd,WM_DESTROY,NULL,NULL);
					}
					
				switch (szIncoming[0]){
			      case 'F':
					sprintf(msgBuf,"G%s",myProgramName);
			        send(Socket,msgBuf,21,0);
					ShowMessagetoText(msgBuf);
					BB->initGame();
					memset(board,-1,sizeof(board));
			        break;
			      case 'S':
					sprintf(msgBuf,"G%s",myProgramName);
			        send(Socket,msgBuf,21,0);
					ShowMessagetoText(msgBuf);
					BB->initGame();
					memset(board,-1,sizeof(board));
			        break;
			      case 'J':
							//doEndGame();
			        break;
			      case'N'://the name of your opponent
			        if ((inDataLength=recv(Socket,&szIncoming[1],20,0))!=20){
						if (inDataLength==-1){
							nError=WSAGetLastError();
							sprintf(msgBuf,"%d",nError);
							MessageBox(hWnd,
								"connection Error N:",
								msgBuf,
								MB_ICONINFORMATION|MB_OK);
								closesocket(Socket);
						}
			        }
					sprintf(msgBuf,"your Opponent is %s",&szIncoming[1]);
					ShowMessagetoText(msgBuf);
			        break;
			      case 'C'://move generate request
							// 要求 AI 產生走步             get request for move generate
							// 所以 AI 要自行記錄盤面的狀態  AI have to record down the board state
							// 若 AI 產生不合法的走步, 則AI 會再次發送這個request  if   invalid , server will send this request again
							// 若產生出翻棋, 則出去 O A~H, 1~4                        the response is when reveal chess, O, move chess, M
							//                      M A~H, 1~4,A~H, 1~4
							// 所以當收到O 或 M 後, 才能confirm 走步合法      when send out the response, have to wait for server confirm
					  memset(outbuf,0,10);
					  BB->SetTurn(myColor);
					  BB->Play(outbuf);
					  ShowMessagetoText(outbuf);
					  if (outbuf[0]=='M'){
						if (board[outbuf[2]-'1'][outbuf[1]-'A']==0){
							sprintf(msgBuf,"wrong Move  red=%u black=%u",BB->red,BB->black);
							ShowMessagetoText(msgBuf);
							sprintf(msgBuf,"bestSrc=%u bestDest=%u",BB->bestSrc,BB->bestDest);
							ShowMessagetoText(msgBuf);
							for (int q=0;q<16;q++){
								sprintf(msgBuf,"piece %d = %u",q,BB->piece[q]);
								ShowMessagetoText(msgBuf);
							}
						}
						sprintf(msgBuf,"Move  %d ",board[outbuf[2]-'1'][outbuf[1]-'A']);
						ShowMessagetoText(msgBuf);
					  }
					  send(Socket,outbuf,strlen(outbuf),0);
			        break;
			      case 'O':
			      	//server 告知, 什麼位置翻出什麼棋, 包括你和對手的翻棋著手
			      	//格式: O[A-H][1-4][CHESS]
			      	//例如: OA1m = 開 A= column 1(橫放棋盤最左的column)
			      	//                1= row 1 (橫放棋盤最上的row)
			      	//                m= 黑象
              // server response what chess you/your opponent had reveal
							//format :  O[A-H][1-4][CHESS]
							// ex:  OA1m
			  			if ((inDataLength=recv(Socket,&szIncoming[1],3,0))!=3){
								if (inDataLength==-1){
									nError=WSAGetLastError();
									sprintf(msgBuf,"%d",nError);
								  MessageBox(hWnd,
									  "connection Error O:",
									  msgBuf,
									  MB_ICONINFORMATION|MB_OK);
										closesocket(Socket);
										SendMessage(hWnd,WM_DESTROY,NULL,NULL);
								}
						  }
						//OA1m 存放在szIncoming[1] ,szIncoming[2],szIncoming[3] 分別為 如上例 A , 1, 5
							ShowMessagetoText(szIncoming);
							BB->Reveal(ptrans(szIncoming[1]-'A',szIncoming[2]-'1'), chessMap[szIncoming[3]]);
							board[szIncoming[2]-'1'][szIncoming[1]-'A']=chessMap[szIncoming[3]];
							sprintf(msgBuf,"Open  %d ",chessMap[szIncoming[3]-'1'+1]);
							ShowMessagetoText(msgBuf);
			        break;
					  case 'M':
							//server 告知, 包括你和對手的走步著手
							//格式: M[A-H][1-4][CHESS][A-H][1-4][CHESS]
							//例如: MA3NA4p = 紅馬吃黑卒
							
							// server response what chess you/your opponent had move
							//format : M[A-H][1-4][CHESS][A-H][1-4][CHESS]
							// ex: MA3NA4p
			  			if ((inDataLength=recv(Socket,&szIncoming[1],6,0))!=6){
								if (inDataLength==-1){
									nError=WSAGetLastError();
									sprintf(msgBuf,"%d",nError);
								  MessageBox(hWnd,
									  "connection Error M:",
									  msgBuf,
									  MB_ICONINFORMATION|MB_OK);
										closesocket(Socket);
										SendMessage(hWnd,WM_DESTROY,NULL,NULL);
								}
						  }
							//存放在szIncoming[1] ~ szIncoming[6]
						ShowMessagetoText(szIncoming);
						BB->Move(ptrans(szIncoming[1]-'A',szIncoming[2]-'1'), ptrans(szIncoming[4]-'A',szIncoming[5]-'1'));
						sprintf(msgBuf,"Move  %d,%d to %d,%d ",ptrans(szIncoming[1]-'A',szIncoming[2]-'1'),board[szIncoming[2]-'1'][szIncoming[1]-'A'],ptrans(szIncoming[4]-'A',szIncoming[5]-'1'),board[szIncoming[5]-'1'][szIncoming[4]-'A']);
						ShowMessagetoText(msgBuf);
						board[szIncoming[5]-'1'][szIncoming[4]-'A']=board[szIncoming[2]-'1'][szIncoming[1]-'A'];
						board[szIncoming[2]-'1'][szIncoming[1]-'A']=0;
			        break;
						case 'U':
			  			if ((inDataLength=recv(Socket,&szIncoming[1],1,0))!=1){
								if (inDataLength==-1){
									nError=WSAGetLastError();
									sprintf(msgBuf,"%d",nError);
								  MessageBox(hWnd,
									  "connection Error U:",
									  msgBuf,
									  MB_ICONINFORMATION|MB_OK);
										closesocket(Socket);
										SendMessage(hWnd,WM_DESTROY,NULL,NULL);
								}
						  }
							switch(szIncoming[1]){
								case 'R'://你持紅子 you get red side
									BB->SetTurn(0);
									myColor=0;
									ShowMessagetoText(szIncoming);
									break;
								case 'B'://你持黑子  you get black side
									BB->SetTurn(1);
									myColor=1;
									ShowMessagetoText(szIncoming);
									break;
								case 'W'://你贏了   you win
									break; 
								case 'L'://你輸了  you lose
									break;
								case 'D'://雙方平手  draw game
									break;
							}
							break;
						case 'X'://通知下一回合開始  request start next game
							//答應
							BB->initGame();
							memset(board,-1,sizeof(board));
							ZeroMemory(szHistory,sizeof(szHistory));
							historyLength=0;
							c='X'; // response confirm for next game
							send(Socket,&c,1,0);
							break;
						case 'D'://通知全部回合結束, SERVER 會進行斷線
							break;
						case 'T'://通知接收盤面現況
			        if ((inDataLength=recv(Socket,&szIncoming[1],64,0))!=64){
								if (inDataLength==-1){
									nError=WSAGetLastError();
									sprintf(msgBuf,"%d",nError);
								  MessageBox(hWnd,
									  "connection Error T:",
									  msgBuf,
									  MB_ICONINFORMATION|MB_OK);
										closesocket(Socket);
										SendMessage(hWnd,WM_DESTROY,NULL,NULL);
								}
			        }
			    }
				}
				break;

				case FD_CLOSE:
				{
					MessageBox(hWnd,
						"Server closed connection",
						"Connection closed!",
						MB_ICONINFORMATION|MB_OK);
					closesocket(Socket);
					//SendMessage(hWnd,WM_DESTROY,NULL,NULL);
				}
				break;
			}
			WSAAsyncSelect(Socket,hWnd,WM_SOCKET,(FD_CLOSE|FD_READ));
		} 
	}

	return DefWindowProc(hWnd,msg,wParam,lParam);
}