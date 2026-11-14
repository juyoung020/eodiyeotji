//! 시연 기록의 주행을 실제 길 모양으로 그리기 위한 길 모양 표 (`demo.rs`가 쓴다).
//!
//! 만든 것: `scripts/demo-roads/fetch_roads.py` (손으로 고치지 않는다 — 구간을 더하려면 스크립트의 PAIRS에 더하고 다시 실행).
//! 출처: OSRM 공개 서버의 차 길찾기, 자료 © OpenStreetMap 기여자 (ODbL). 10m 오차로 줄인 구글 폴리라인(소수 5자리) 글.
//! 한 줄: (출발 장소, 도착 장소, 길 모양). 반대 방향은 같은 길을 뒤집어 쓴다.

use super::*;

#[rustfmt::skip]
pub(super) const ROADS: &[(usize, usize, &str)] = &[
    // 115.2km · 점 277개
    (HOME, HOENGSEONG, r#"eqedFegjfWxDeA[uD\{@mIvAi@`@rAK`f@wJcGiEaDmD_HcMmCqMVcIk@oS|Eqa@zFgRvDeTt@wPk@{\t@oQr@uInBoLdGgQbBoMhCidAjBaI|AsD|NkOyLch@uAsCsFcFbYg|A~JyR`\y{AjBiLL{COcHeAcGgaAe|CeGuUaLy^EwFhA}EhD_DzUkL`ZcLnHmEdIuHnD{EbIcQnCkKfCiVdDohBpAiJfDkJtGoIrJcF~F_A~b@qBvIqBpMcGzP}Jvb@uSjuAi~@|CwAdQoFf]{FrKqCrMsG|TiPlSiIzwAyXjK{CjGeDjGiF|FeHbHqNjHeYdW{kAfHgUxF{MjTee@nKiQpMyNhPyKnOiGhr@kO`QsFjHsCrjAcl@h}@iY`d@iP|HaB|XeBv~@iDpBq@zA}AhAwCZsCj@y\MkDaAoCeHmGcAmCg@qDg@eb@tAgjCOmMmEo}@}Cc\oXmkBoC}ZByPdCyp@hBaXbDkpAwBymCQyv@|OivDjA}a@]_zAiBs[iDwZm@oUDuJr@eNxE_d@PgVkEae@cUqnBkTapAgA}Lm@sSPsVzAgT~G{r@d@}RWmOaEscAmBac@aAyKiAyImGwZ{g@}nAci@oiBeDmSoAw\R{QxCkh@~GguCcAaTuH{a@oA_Kq@qMAmJjAqPz\g{Bz@aKVgLkAeW_AoGsKcf@iBeP_@}On@u_BY_\}AkQiPe~@cAeNE}PbA_QxEsb@fIgm@tZqwAvXufA`CeMzA_NfAi]GgJ_AiRsDcYuQ{eAyA_UOwV`@yMtA_QbDkSrD{N|a@soAhEoTt@qMMqPcBiPeH_b@iA}PYgNJgLr@}M~I_t@z@mMJ{L{AwEaCeB}PcDmq@iTcHmFaGoJiDuLcAaMr@iSbBsNfAqClBsA|Pe@PmHai@gMcPsJu\sX}PuIgNsB}]_DoViHyIoG_IaK{BwFsFyRiGwJoG_GyTyKqJyCqO_AoV`C_Ob@_GWuTqDoj@hC{Cn@sDxBoBzBwEfJmBnB{CnAsD\aK}B}KwI_H}DmWwGiMmIyQcAqAyAkBwDsBqJyJcZuBuImVouAa@wSJw@|I}KhF}EpBiEfBsBrGu@zCyA|Fg@fDeKzDcEfB{IjIiFdN{TlFgBdEQzGkDdF}GmG_HZkCEs@_@I"#),
    // 113.7km · 점 370개
    (HOENGSEONG, GYEONGPO, r#"sjucFelllW^HDr@[jClG~G`LeS|BkGy@oDkDcAq@}@c@{QVsFc@sAwBwBe@eB`DcIf@gHIiD{GcQEaCkB[{@y@mBmE{Aq@eFr@wE_@oGcBWeAdGsH~Q{c@hFgFfRaMtB{B`MkVdTs[tDuBhMuCtIiEdFqElJoLlARzKzP`SvQz@nCi@zC_AZy@Si@_AGsB`Dmd@pAwJjSsz@bV_p@fAkLUeKq@cFeAsDgD{GwDcE}EsCcFsAsFUgQ|BqECaG_AoFwCaf@m^aKoE{IoA}LTuGtAePrHyHr@aEa@oDkAkEmDyCgFqCsJs@mGE{Kh@kGnDeOdHcUlCoGxEgGtJ_JbD}EhB_FbImm@ZwHEmKc@eIy@aF{Lsa@uEaKmEcHaNyNo\_Ui[sc@}EaMqAcHcCgVkEaPkF{IsQuRgFoHqCwHwBgPQ_r@oD_`@c@aT}@gM_EgUwHoUsBiKw@kMJuYoAwOsRwv@{GgQuByE{DoFqQwNmDyDiK{S_EiGaG}EeOyI}HsHiW}b@_I{SsL}k@_AyKe@{d@}AcQoAyFcPwe@_GuKw`Dw|EmImNaDuHsCyKoAiIsHuv@wCmUyQun@qCmUK{TbBij@|Gg_ATgIa@wN_DwQyCwHwF{IqpAw~A_JoOoJyRcZaf@{IkImS_K}HiHsEyH_HmSmEyHqFgF}GqDm]gG{F_C{GkFiEeGqOsZyAiH]yGf@}JhDeQxAqNfFqjAPkJ[aJmAaJmBmH_GgLcJqIuKqEcPiCaJiCm\mTwGwGaEiGaSq_@oDuIkBkJwBqYuBoJoCeH{D{G_FgGyi@ug@cEuG_EiLwBcQib@{sGeByRwAiIiG_U}LqZcLk^cDcOeOg{@{C{MmE{KgNgUuFgLiX}eAoEiUoEee@iH{e@iFsjAyAgOaPej@oJup@cCoMuGcQ{Zog@sEcJcDyKwB}Q?sQd@mGhBiKvFyOlSaYpC}FlEoOtAaLPmVsEulBv@iXrDeXtDwN~FcOlu@wuAnFwLbHyU~DoVbBgXJsVoAwUcDu]AuOlAy[y@kMuAwGuEcL}GeIeJgF}JcBae@Pw~@tBmWlFu_@tS}FnAsGZmLgAuGaCqFuDkO}OeFsCqF_BgMk@mGv@uQ~EkG\cHa@yJkDod@_a@uD_CiGyBwGy@oGDcSzDsG^qIo@{HgCsEyC{EgFiMcWaKyMsKiHaY{KiJmH}GwHqGcMwEsOoEyWyC_IwE_IwJ}Jgh@a\yFkGeDeGoDaLaBcOMyMr@{]vAqL`EeKhG}GtIqDzk@iLb\oDzQD|DhBjDrDdD_@z@qBTuB`Cyc@|AuFp|@mp@rCcE^uEQ_BoA}AsBS_J^cHkBkMoGcCiCqBgEw@oD}@gKx@wXgEek@WoKk@qCc@q@wBu@mKy@aJkBeLoJkFyGkBZyd@MaId@wOmEmRw@{CDmDhAiFtDmRoRiRqD}LyHgHiKqG}BaBwAeDmLqBcD{JuAqf@sP_WkFgF_DaE{EwLeUgI{HmFeD_AkFmCqDNU"#),
    // 1.9km · 점 10개
    (GYEONGPO, CHODANG, r#"ywveFigxqWpPwSrFgFfHmJjEuI~BiCtF^pD}AxPzObGo@"#),
    // 4.3km · 점 17개
    (CHODANG, ANMOK, r#"ycteFipyqWcGn@{IwHpDyJxKiKwBsE|OyPrCoGzGqKdAcF|[sc@vAaDvm@o~@hEeESyA~AyDbCqC"#),
    // 0.7km · 점 10개
    (ANMOK, SEAFOOD, r#"ilpeF{a`rWtCmDxBoE\iCZMMeAnA_I~A_AjA@fA`C"#),
    // 215.7km · 점 553개
    (SEAFOOD, HOME, r#"ixoeFs|`rWPzCSx@kGnH_BjHeFhGkHxPh@zTaBtV@zEs@rFkClK_AvKxBbGdGvUvQpb@tYhy@~_@`c@`LdObLbPDx@t@P|BjDhQn\jTbOnEjSBrFtAhSJzK~ApQS|@p@nAp@dGvDti@h@xLw@dSvAnO~@xC~CxEh\vRvAzB\tBc@jEaC|Cel@ra@mElEgBvEmBlXqA|CyCdC}Dp@k^n@yUrCqRnDoMbA{Q\aGtAiFpDkCvDaBdEcArFYjFj@`s@`AnLtA`HhFxMvHnJdk@b^bKpKhD`G`DfI`BzGvBtO~EtO~DrIpDjFpPtNpW~J|KbHzJrLdNpXdGtGxGvDjHhB~G`@fHk@pRsDvGArGdAjIlD|`@|]jKhGtGtA~HFhFo@dSkFvGWxF\pGbBxFzChO|O`GdEjGbCvG`AtJH~H{AzHeDnPcKbUwGrLqA~d@iAvx@]`Gd@bH|BlHrFhE`GrBxE`BbGpAtKDhKoAla@~Cha@h@tMEv^aBfX_EnVcHvU}Yvn@ec@ry@mEvLwEzSkClX[lStEziBOjVwA~KkEjOoCzFiPfTmEpI{A|EgCvLo@nGWzHJtJvBbRhE`Nl`@pp@pGxPpOrbAhLt_@fBpIz@xIhD~y@tAdTjGla@pDr`@tDzTjUj_AhE|NvFfLvKdQ`GhOhXnwAzHlWtP~b@vFfUjDh\tb@vuGhB`NpCzIbHhLvi@dg@fI|KvDlIzC|LvCh^xBxHjUhd@bIxLrEfEp\lTdJhC`PlCpKlE~H`H~GlMjBlHnA`JRjPsFloAyAnNgDbQi@dK\~GxAjHvOtZhGdI|ElD~F~Bb[hFhGpCzFnEpEvGdJ~VzEnIrH|GnS~JzIfIfZdf@nN|Y|HrM~nA`}AfEhH~B~FtChN|@lQU`IyD`e@mBj]}Alj@b@hU~CnSzPjl@lBjNlJ`aAbE`SxCbIxF|J|}C~xExN`VnNxa@lAxF|AxP\nb@n@|J~Kxk@lEpNvCpGnWlc@fJxIbOjIjDpC|EzGxK|TnWdUfC|CzCrGzGxR|Rfv@dAfOQrWx@bObBzIlJxYdDrSt@xJb@pTrDna@?zk@l@pJnArIxEzKrXp[lEhInDrMbEn]vB`I|CvG~Yha@rHlGtQfLpOzO|DdGzFfMjIbYxClPXjQi@pLgGpd@cBlHoBjDkE|FuIpHcExGcOxe@mAhJKjJ~@pKxA~FjBnE~ChExCxBpGfBrFDpDq@|IoE`NyD|LKpIfAfMdGtc@t\rFvCbGbAdDBvR_CpCDvKvCrHxGjFlMfAjNIzE_AzHcV~o@wOpn@iE`W}S~vCOnG`@lNjAhJhBvGbB~E~EnInX~\|I~QnCrJ`CvNjGf|@|BzJ~EfJzIrGrR|Hp_@|Qh_Axi@zHbIv]dg@hG~KtEvKrEbOlD`QjB`Qx@`S?`fAX|GbBbK|Mtf@|A`KB|MyCfc@X|K~AvJxEnLhFbHjBtA|HnDf|@pZtCtBpArEaAjVcLj}@k@rRX|Nx@dJhIlc@pBhO`@vN_@pN}@jIaC`Lca@`nA{EnQwEvZsAjWBfXjAhSrVhcBhAtW[`YcBvR_BfKk]xuAkUvgAmO~gAyCvW]~IB~KtAhPxPp`AzA|Q\d[e@dgBb@tMnCbSbK`f@bBtTQxMyAbQ}YjnB_BjLg@nKBvLn@tKfKrl@bA`TcGnjCiExy@Q|U|AzVtExUbf@h_B`i@zrA|EvTjCzP~AjVzEpkA`AjW@|KgApV{Il_ASxQThP`BjUlW`~A|Qz_BhEne@KbVsExc@}@jZl@dThDj\nBb\LxtAiRpuEVp~@nB`jCuDntAyAjW_Cnt@KzMzB~ZtXrkBtCf\nEp|@JzKeAfcCx@rg@VvExA|FpBbDjGnGh@rBFnOgB~c@wDbY_GnSwGzPiTjc@aDxKeD~PgDfYsBnLyn@psCcB|K_CjV{ClgA}AvW_BdMcPx}@eGzp@uBfJgSfa@iBbFaEhSm@jIQhMd@~|@oAnTkCjOib@ziA}GpTuBrJqB~UiCfPaD~LyEdMaFlJ{JrMsc@~^}GrBeBtCkJzmAoAbXGpT{E|H}]rBxCn[IlHe@pBgCtA}BPkd@uB}GwAa_@qLeJkB{^V}X~A}i@kAqGN}IvA_JfDk`Ann@kGlEoClDwAxEsAxJ}ClGgc@p\eGbHwDlIwKtZcBvGib@rgB_Ipm@}A~EkCnEaF|DmGrBymAjLgHtDsKlL_EfAmF]si@cRoDv@mBtD_Kxf@mC~QwQ`bAoAfEo@f@wFMaN}AgCvQsFbZo@tAyRa@g^bHaJxBlC?`Du@Zi@h@vEyDdA"#),
    // 1.7km · 점 10개
    (HOME, SUNDUBU, r#"eqedFegjfWxDeAbHdD|AfBvAdFg@~C|IxSZnCaAvm@p@h@"#),
    // 6.4km · 점 29개
    (HOME, OFFICE, r#"eqedFegjfWxDeAbHdD|AfBvAdFg@~CfJ|TPjByA||@d@hDqBv@aR|MeQdOiNnIqCpC}DrI[rFRxHc@hCiXxA~K|w@?dMk@lGDrCuGf@gKzEyHRTbSwEE"#),
    // 1.6km · 점 11개
    (HOME, STARBUCKS, r#"eqedFegjfWxDeA[uD`@g@Ua@gNnD}^hAwFsGsFuBPqCbCU"#),
    // 4.9km · 점 15개
    (STARBUCKS, OFFICE, r#"wegdFc{jfWRAVvG{@n@iFjJcIbU{ClR[tRyA|NElGxCvq@oFrWcDb^`DlpBwEE"#),
    // 5.2km · 점 29개
    (HOME, GAS, r#"eqedFegjfWxDeA[uD`@g@Ua@gNnDo\xAmBg@uEgFwIkDgTiAzBq_@_GcLBcB~EgK^qErNcH|@uArE}BfNaFfF}A`IVzMxCnBtAjBhDt@|DTpK`BtWvA|H"#),
    // 8.5km · 점 40개
    (GAS, OFFICE, r#"ahedF}skfWbB~E|BzDfOjP|QtWr@hB?~@s@`AiAGq@mB`@}ElE}ONuBeL|d@L`EzCfLtAvK}AFmOoFqArw@d@hDqBv@aR|MeQdOiNnIqCpC}DrI[rFRxHc@hCiXxA~K|w@?dMk@lGDrCuGf@gKzEyHRTbSwEE"#),
    // 6.1km · 점 25개
    (OFFICE, HOSPITAL, r#"}ihdFet`fWjADFwQhCGmCq~ApDs_@tEiRgDmt@HkHhBqOVaRfB}L|I_XnHqMhEmCnGoGzDuLf@uEeNuAIqBn@}@hBIJoDp@eBc@aD"#),
    // 2.2km · 점 24개
    (HOSPITAL, HOME, r#"ixfdF_jlfWb@`Dq@dBKnD}AB{@bAHpBzHlAzBpA_AnHiCjG_BrAx@XbA~CNdA_@|B|@pFtWmA`Cg@t@PxFgAZi@h@vEyDdA"#),
    // 8.2km · 점 25개
    (OFFICE, EMART, r#"}ihdFet`fWjADFwQhCGmCq~ApDs_@tEiRaDqn@CaKpBwRZ_SjCkOtHsTfE_IfBqChEmCnGoGzDuLfBgOtB_DfQoB|v@qF~QxB`Hc\nEyX"#),
    // 3.1km · 점 14개
    (EMART, HOME, r#"_|bdFo`nfWoExXkHb]}BvPuGl\kCX{No@g^bHaJxBlC?`Du@Zi@h@vEyDdA"#),
    // 52.6km · 점 169개
    (OFFICE, NAMYANG, r#"}ihdFet`fWvEDBxJ|PhCzFrCdBtFvAtMrA~FZ~FtAvAfCl@`MjJvMPdH]nVxDrJAzMi@pt@uL|BDnB|@dHhGxn@tl@zQe_@pAsMtLek@lEeQnAuAhCa@zPPnrAjU~@Sn@}@ZsG~BmHvC_GdCoBlEp@`GpFtGtApc@j@`KdAz`@dAbm@t@rGmAxQiHnF}DzL{N|GqDpE_AjFAbU|CvCWtQaL`NuEvQuMzz@y`@lo@wXrDShEj@dc@lPjF`Djk@vd@hGhDbD|@~ELfNUdGrArCxAjCdDbUfe@vAzIq@zSdFbBdMdItYq@fz@`X~k@rDzl@t_@nE`HbDbNzCpHfNvq@bSbe@hBhCbDdAnI_GfGuBrE_@jLh@hIrDvM`BtC|AdFjF|Q`CrCjAlJfHnJbKxPnLfQnD~FxB~G|FvEfIjSvx@fE|IzD~DdNhJtVbNpSpI|{@vRrD@dHgA`EgB|P_NnEs@`DDp{@|U`s@pVvNz@j]]vHx@dnAb`@h\fGhFvB`FlDl[d]~O~LhFzKjDpCjWrNjDxD`D`GxBbIfFdZVrJkBhYHnNpGlj@fCpHvBtBhEjB|TvE|SxBdDdAfDnC~MvT|GvP`BdHNbHcA|PFnFlKlz@hAf^j@lG`BbGhZzs@h@AxE}EhFwOnZcP|IoIfDgKxAuAvC_AlBLdCfAtDnGfDwDjBdDpBoB"#),
    // 21.7km · 점 46개
    (OFFICE, PANGYO, r#"}ihdFet`fWjADFwQfKKxLuFtJaAxBiBdCeIjBwBxNoCpGyBjlA{o@tD}DlDmHjP{DxGgChg@m]~o@wl@~FcB|CKjWnKbGzAvKl@fHe@vZoHbhBwg@xoAs\vJ}C`GaDpuBsnB|n@sj@dPsM|d@oZznDwoDjKsH\}@LsDeAeI?aBh@iBy@oNqAmCuGwEGe@qCK`Br@"#),
    // 157.5km · 점 244개
    (OFFICE, DCC, r#"}ihdFet`fWjADFwQfKKxLuFtJaAxBiBdCeIjBwBxNoCpGyBjlA{o@tD}DlDmHjP{DxGgChg@m]~o@wl@~FcB|CKjWnKbGzAvKl@fHe@vZoHbhBwg@xoAs\|O_G~SiQlcBi~A|n@sj@dPsM|d@oZ`|Dq|Dfk@em@pEyCvFyAztAyNzV}@hkYsC`ReAfaAgPvEQrH|@bDxAxbAnq@rOvGzMhDxJnAdOXdnCLld@eCrUt@tQfEhqClbAtJtAhMPrLsB|p@aP|GcD|FwEpGaIhuAcwBjLsKlNmHlrEo~A~LuFd[qSzPaHjSaCnpC}L~XqDb|Bo_@dP{Eh|@m_@lvByk@tRgJxbFuwDzEqCvFeBnKk@`qC~BjwEgA`Wt@bxBxRvIvAjdBlw@n_AfLrH~D|m@fa@rGrCvK|Bjj@fCj|@~B~JyAtq@sP|w@{^jMoDjP}Cho@gSrKkGnCoCxH}LhBwEtCkNx@aP~BgvAfAkLtFaSrVmq@fF}GrHaDrf@yBbLgCbLiIjEgH~ByGzAiHrFyaArByOpJsVhz@}iBnDyLfAwKAoLeR{uBQaIf@iJnAyGrn@otBfN{tAnRqm@lVcwAnBgHhDgG~BcCxFcD|D_AjEQ~H~@vU`IlGThHeA~HsEta@k[po@sWlFyE`FcJhBkHzB{QnAqFxF{LxP_PjWcQvFkCnJoAnIp@rg@zRlM`CxMAzGq@tk@_OzaDy}@zRyFvOwGdI_HdGqIdcAesBhGiNzEqNtCiF~O}ObPmVdFuFzZoT~JkEvNyBtu@d@dHk@lmB{`@dLwAne@tCpWeBtHDf]dEb}@hFjH}@|XqKfIy@fKzAhVpL`r@nG~|DiKtc@lIpJ~DpClCbDbFnQ|_@fIdN|HzGnCnAtE|AdKfA`Ha@d}AaUhJCzC|@xCzBfBLpAcAt@aD~Bij@bWpHnb@pUvd@nKpE?`HqAxVwPjDcAvDUlA^pAzAtAlFxAfBlL~Cfg@tVv\nJ~QpDv]jBrx@pAhQ|AjHhBtD~B}HfKgEdMjMnGvAbBdBpDdCdLbB~QnG|_@RrDN`XsFB"#),
];
